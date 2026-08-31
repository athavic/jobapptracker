# Job Application Tracker

A job application tracker with an automation dashboard. Four services, one source of truth.

| Piece | Stack | Status |
|---|---|---|
| Database | PostgreSQL 17 (Docker) | **phase 0 — working** |
| `api/` | Spring Boot 3.5 · Java 17 | **phase 1 — working** |
| `web/` | React 19 · TypeScript 5 · Vite 8 · Tailwind 4 | **phase 2 — working** |
| `automation/` | Python 3.14 · httpx · pydantic | **phase 3 — working** |

Architecture and full build plan: [Job Tracker Blueprint](https://claude.ai/code/artifact/e244d427-b199-4c28-83c6-b5f85d882342)

---

## Before you start

You need Docker Desktop **running**, Maven (which uses `JAVA_HOME`, currently JDK 17),
Node, and — for `automation/` — Python 3.13+.

> **Port note:** this machine already runs a native PostgreSQL 18 Windows service on
> **5432**. The Docker container therefore publishes **5433** on the host. If you ever see
> `password authentication failed for user "jobtracker"`, you are talking to the wrong
> Postgres — check the port before you check the password.

## Run it

One command, from the repo root:

```bash
npm run dev
```

That starts Postgres and waits for its healthcheck to pass, then runs the API and the
Vite dev server together with their logs interleaved and prefixed `[api]` / `[web]`.
Ctrl-C stops both.

The wait matters: Spring Boot does not retry its database connection, so starting it
before Postgres is ready fails outright at Flyway.

Run `npm install` once at the root first, to get the task runner.

<details>
<summary>Or run the three processes yourself, in this order</summary>

```bash
docker compose up -d
```

```bash
cd api && mvn spring-boot:run
```

```bash
cd web && npm run dev
```

</details>

### Every script

| | |
|---|---|
| `npm run dev` | database, API and web together |
| `npm run dev:stop` | free ports 8080 and 5173 after a crash or a closed terminal |
| `npm run dev:api` / `dev:web` | one server on its own |
| `npm test` | the API test suite |
| `npm run automation` | the `nudge_stale` job - reports, changes nothing |
| `npm run automation:test` | the Python test suite |
| `npm run automation:install` | create `automation/.venv` and install the worker |
| `npm run build` | package the API and build the web bundle |
| `npm run generate:api` | regenerate TypeScript types from the running API |
| `npm run db:up` / `db:down` | start or stop Postgres |
| `npm run db:reset` | **destroys the data** and recreates an empty schema |
| `npm run db:psql` | a psql shell inside the container |

### Changing code while it runs

**Front end** — Vite hot-reloads. Save a file in `web/` and the browser updates in under
a second, keeping React state. Nothing to restart.

**Back end** — `spring-boot-devtools` restarts the API when its compiled classes change,
in under a second rather than the ~4s of a cold boot. But something has to do the
compiling: hit Build in the IDE (Ctrl+F9 in IntelliJ), or run `mvn -f api/pom.xml compile`
in another terminal. Editing a `.java` file alone does nothing until it is compiled.

devtools is `optional` and `runtime`-scoped, so Spring leaves it out of the packaged jar.
It never ships.

`dev:stop` exists because Ctrl-C is not the only way a dev server dies. Close the terminal
window or stop it from the IDE and, on Windows, the child java and node processes outlive
their parent and keep holding the ports - so the next `npm run dev` fails with
`Port 5173 is already in use`. `dev:stop` clears them. It never touches Postgres.

| | |
|---|---|
| App | <http://localhost:5173> |
| API | <http://localhost:8080/api/v1/applications> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI spec | <http://localhost:8080/v3/api-docs> |

## Test it

```bash
npm test
```

Twenty-one tests, no database needed: the lifecycle rules run as plain unit tests, and
the controllers run as `@WebMvcTest` slices with the service mocked.

```bash
npm run automation:test
```

Thirty-one more on the Python side. The eight contract tests in that suite check the
models against the live `/v3/api-docs` and skip themselves when the API is not running,
so run them with `npm run dev:api` up after any backend change.

```bash
npm run build
```

Runs `tsc -b` before bundling, so type errors fail the build.

## Poke at the database directly

```bash
npm run db:psql
```

`\dt` lists tables, `\d job_application` describes one, `\q` quits.
`flyway_schema_history` is Flyway's own bookkeeping — look at it to see which migrations
have run.

---

## The contract between `api/` and `web/`

This is the part worth understanding, because it is what makes the two halves safe to
change independently.

```
Java DTO records  ──springdoc──>  /v3/api-docs  ──openapi-typescript──>  web/src/api/schema.d.ts
```

**After changing any DTO, enum, or endpoint in `api/`:**

```bash
cd web && npm run generate:api
```

with the API running (`npm run generate:api` from the root does the same). Then
`npm run build` — TypeScript will name every component that
depended on the old shape. That is the whole point: the compiler, not a runtime 500, tells
you what a backend change broke.

`schema.d.ts` is committed deliberately. The app builds without a running API, and a diff
on that file shows exactly how the contract moved.

Two annotations make the generated types usable, and both are easy to forget:

- **`@Schema(requiredMode = REQUIRED)`** on response fields that are always present.
  Without it springdoc calls everything optional and the TypeScript is all `| undefined`.
- **`@ParameterObject`** on the `Pageable` argument. Without it springdoc documents one
  opaque `pageable` object instead of the flat `page` / `size` / `sort` params Spring
  actually binds.

---

## What is built

### `api/` — the system of record

```
com/jobtracker/
  application/
    ApplicationController.java      HTTP only — routing, status codes, @Valid
    ApplicationService.java         all the decisions live here
    ApplicationSpecs.java           composable filters + the fetch-join
    ApplicationMapper.java          entity -> DTO, inside the transaction
    ApplicationStatus.java          the lifecycle and its legal transitions
    JobApplication.java             @Entity
    dto/                            records — the API contract
  company/
  common/
    GlobalExceptionHandler.java     every error as an RFC 9457 problem detail
    WebConfig.java                  CORS for the Vite dev server
resources/db/migration/
  V1__initial_schema.sql            company + job_application
```

| | Path | |
|---|---|---|
| `GET` | `/api/v1/applications` | `?status=&company=&includeArchived=&page=&size=&sort=` |
| `POST` | `/api/v1/applications` | 201 + `Location` header |
| `GET` | `/api/v1/applications/{id}` | |
| `PATCH` | `/api/v1/applications/{id}` | null fields mean "leave alone" |
| `POST` | `/api/v1/applications/{id}/status` | the only way status changes |
| `POST` | `/api/v1/applications/{id}/archive` | |
| `DELETE` | `/api/v1/applications/{id}` | really deletes; prefer archive |
| `GET` | `/api/v1/automation/runs` | `?jobName=&page=&size=` |
| `GET` | `/api/v1/automation/runs/latest` | one row per job, for "last ran" |
| `POST` | `/api/v1/automation/runs` | a job announcing it started |
| `POST` | `/api/v1/automation/runs/{id}/complete` | its outcome; twice is a 409 |

### `web/` — the dashboard

```
web/src/
  api/
    schema.d.ts        generated — do not hand-edit
    client.ts          typed client, ApiError, unwrap()
  features/applications/
    ApplicationsPage.tsx    filters + paging
    ApplicationsTable.tsx   rows + the status control
    CreateApplicationForm.tsx
    StatusBadge.tsx
    hooks.ts                queries, mutations, query keys
  components/ErrorNotice.tsx
  lib/                 formatting, query client
```

### Decisions worth understanding

- **Flyway owns the schema; Hibernate only validates it.** `ddl-auto: validate` means
  startup fails loudly if the entities and the migrations disagree. That failure is the
  feature. Never edit an applied migration — add `V2__...sql`.
- **`open-in-view: false`.** No lazy loading outside a transaction, so the service maps to
  DTOs before returning. That is why `ApplicationMapper` is called where it is.
- **The list query is a fetch join, not N+1.** One request issues two statements: the
  paging count and one joined select. `show-sql: true` is on — watch the log and count
  them when you change the query.
- **Status is not a PATCH field.** It moves through its own endpoint so a transition can
  never be smuggled in as an ordinary field update.
- **The UI has no copy of the lifecycle.** The status dropdown renders from
  `allowedNextStatuses`, which the server computes from the same enum it enforces. The
  rules cannot drift because they exist once.
- **Mutations invalidate, they do not patch the cache.** Simpler, and always correct.

---

### `automation/` — the Python workers

```
automation/src/jobtracker_automation/
  config.py            pydantic-settings; CLI > env > .env > default
  models.py            pydantic mirrors of the DTOs
  client.py            httpx client, paging, ApiError
  runs.py              the start/complete context manager
  jobs/nudge_stale.py  the job; select_stale is a pure function
  cli.py               argparse, logging, exit codes
```

`nudge_stale` finds applications that have been applied to, are still open, and have
not moved in N days. Full detail in [automation/README.md](automation/README.md); the
decisions worth carrying to the next job:

- **Workers talk to the API, never to Postgres.** Direct SQL would be shorter and would
  create a second place where the lifecycle is decided. One system of record is the
  point of the seam.
- **Python has no copy of the lifecycle either.** "Still open" means
  `allowedNextStatuses` came back empty; before ghosting anything the job asks that same
  field whether `GHOSTED` is legal. Same rule as the React dropdown, same single source.
- **A run is recorded in two calls, not one.** Start before the work, complete after. A
  job that is killed then leaves a visible `RUNNING` row instead of nothing — and
  nothing is indistinguishable from never scheduled.
- **Jobs read by default.** `updated_at` is the only staleness signal, so a job that
  writes erases the staleness it was measuring. Writing takes `--apply`.
- **Idempotency comes from the state machine.** Re-running the ghosting is safe because
  `GHOSTED` is terminal, so the next scan filters those rows out. No "already processed"
  flag to keep in sync.
- **Hand-written models, checked against the live spec.** `tests/test_contract.py` reads
  `/v3/api-docs` and fails if a field, a required marker, or an enum value has drifted.
  It is the Python answer to what `generate:api` plus `tsc` does for the front end.

---

## Next: phase 4

`application_event` — a row per status change, written inside the same transaction as
the change itself. That is what makes the timeline real and answers the question the
automation makes worth asking: *did I move this, or did a bot?*

`ApplicationService.changeStatus` and `ChangeStatusRequest.note` are already shaped for
it; the note is currently accepted and dropped.
