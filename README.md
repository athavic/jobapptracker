# Job Application Tracker

A job application tracker with an automation dashboard. Four services, one source of truth.

| Piece | Stack | Status |
|---|---|---|
| `api/` | Spring Boot 3.5 · Java 17 | **Phase 1 — working** |
| Database | PostgreSQL 17 (Docker) | **Phase 0 — working** |
| `web/` | React · TypeScript · Vite | not started (phase 2) |
| `automation/` | Python 3.14 | not started (phase 3) |

Architecture and full build plan: [Job Tracker Blueprint](https://claude.ai/code/artifact/e244d427-b199-4c28-83c6-b5f85d882342)

---

## Before you start

You need Docker Desktop **running**, and Maven (which uses `JAVA_HOME`, currently JDK 17).

> **Port note:** this machine already runs a native PostgreSQL 18 Windows service on
> **5432**. The Docker container therefore publishes **5433** on the host. If you ever see
> `password authentication failed for user "jobtracker"`, you are talking to the wrong
> Postgres — check the port before you check the password.

## Run it

```bash
docker compose up -d
```

Then, in a second terminal:

```bash
cd api && mvn spring-boot:run
```

- API: <http://localhost:8080/api/v1/applications>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI spec: <http://localhost:8080/v3/api-docs> — phase 2 generates TypeScript types from this

Stop the database with:

```bash
docker compose down
```

Add `-v` to that command to also delete the data volume and start from an empty schema.

## Test it

```bash
cd api && mvn test
```

Ten tests, no database needed: the lifecycle rules run as plain unit tests, and the
controller runs as a `@WebMvcTest` slice with the service mocked.

## Poke at the database directly

```bash
docker exec -it jobtracker-postgres psql -U jobtracker -d jobtracker
```

Useful once you are in: `\dt` lists tables, `\d job_application` describes one,
`\q` quits. `flyway_schema_history` is Flyway's own bookkeeping table — look at it
to see exactly which migrations have run.

---

## What phase 1 actually built

```
api/src/main/java/com/jobtracker/
  JobTrackerApplication.java
  application/
    ApplicationController.java      HTTP only — routing, status codes, @Valid
    ApplicationService.java         all the decisions live here
    ApplicationSpecs.java           composable filters + the fetch-join
    ApplicationMapper.java          entity -> DTO, inside the transaction
    ApplicationStatus.java          the lifecycle and its legal transitions
    JobApplication.java             @Entity
    JobApplicationRepository.java
    dto/                            records — the API contract
  company/
  common/
    GlobalExceptionHandler.java     every error as RFC 9457 problem detail
api/src/main/resources/db/migration/
  V1__initial_schema.sql            company + job_application
```

### Endpoints

| | Path | |
|---|---|---|
| `GET` | `/api/v1/applications` | `?status=&company=&includeArchived=&page=&size=&sort=` |
| `POST` | `/api/v1/applications` | 201 + `Location` header |
| `GET` | `/api/v1/applications/{id}` | |
| `PATCH` | `/api/v1/applications/{id}` | null fields mean "leave alone" |
| `POST` | `/api/v1/applications/{id}/status` | the only way status changes |
| `POST` | `/api/v1/applications/{id}/archive` | |
| `DELETE` | `/api/v1/applications/{id}` | really deletes; prefer archive |

Try the interesting one:

```bash
curl -X POST http://localhost:8080/api/v1/applications/1/status -H "Content-Type: application/json" -d "{\"status\":\"ACCEPTED\"}"
```

From `SAVED` that returns **409** with a message naming the transitions that *are* legal.
That rule lives in `ApplicationStatus`, gets enforced in `ApplicationService.changeStatus`,
and will be the same rule the Python email job hits in phase 5.

### Decisions worth understanding

- **Flyway owns the schema; Hibernate only validates it.** `ddl-auto: validate` means
  startup fails loudly if the entities and the migrations disagree. That failure is the
  feature. Never edit an applied migration — add `V2__...sql`.
- **`open-in-view: false`.** No lazy loading outside a transaction, so the service must map
  to DTOs before returning. This is why `ApplicationMapper` is called where it is.
- **The list query is a fetch join, not N+1.** `ApplicationSpecs.fetchCompany()` loads
  companies in the same query. `show-sql: true` is on — watch the log and count the
  `select` statements when you change something.
- **Status is not a PATCH field.** It moves through its own endpoint so a transition can
  never be smuggled in as an ordinary field update.
- **DTOs, never entities, at the HTTP boundary.** A column rename should not be a breaking
  API change.

### Sample data

Two applications (Stripe, Linear) are already in the database from the smoke test. Wipe
them with `docker compose down -v` if you want a clean slate.

---

## Next: phase 2

Scaffold `web/` with Vite, generate TypeScript types from `/v3/api-docs`, and build the
list plus create form against the running API. You will hit CORS immediately — that is
expected, and the fix goes in `common/` as a `WebMvcConfigurer`.
