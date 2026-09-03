# Pendency

A job application tracker with an automation dashboard. Four services, one source of truth.

> **pendency** *(n.)* — the state of being pending. It is the state most of your
> applications are in most of the time, and the one this app exists to make visible.

Domain: `pendency.app` (`.com` was being resold for $9,999; `.app` was not).

| Piece | Stack | Status |
|---|---|---|
| Database | PostgreSQL 17 (Docker) | **phase 0 — working** |
| `api/` | Spring Boot 3.5 · Java 17 | **phase 1 — working** |
| `web/` | React 19 · TypeScript 5 · Vite 8 · Tailwind 4 | **phase 2 — working** |
| `automation/` | Python 3.14 · httpx · pydantic | **phase 3 — working** |
| History | `application_event` across all three | **phase 4 — working** |
| Multi-user | workspaces · Google sign-in · scoped queries · RLS | **phase 5 — 5a-5e done** |

Architecture and full build plan: [Pendency Blueprint](https://claude.ai/code/artifact/e244d427-b199-4c28-83c6-b5f85d882342)

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

Eighty-seven tests. Most need nothing running: the lifecycle rules are plain unit
tests and the controllers are `@WebMvcTest` slices with the service mocked. The
exceptions are `WorkspaceScopingTest` and `RowLevelSecurityTest`, which start their own
PostgreSQL through Testcontainers, so Docker has to be up for those. That is deliberate
- a workspace leak is a claim about SQL, and a mocked repository returns whatever the
test told it to. `RowLevelSecurityTest` goes further and connects as the unprivileged
role rather than through Spring, because Testcontainers hands the application a
superuser and PostgreSQL exempts superusers from every policy: routed through the usual
beans it would pass against a database with no policies at all.

```bash
npm --prefix web test
```

Thirty-four on the front end, under Vitest: the logic behind the edit form, and the
sign-out path that used to report a refused sign-out as a success.

```bash
npm run automation:test
```

Thirty-seven more on the Python side. The ten contract tests in that suite check the
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
  common/
    Actor.java                      HUMAN / AUTOMATION / SYSTEM
    ActorContext.java               who is calling, without the service knowing HTTP
resources/db/migration/
  V1__initial_schema.sql            company + job_application
  V4__application_event.sql         the history, plus a backfill
  V7__workspace_scoping.sql         workspace_id on everything that holds data
  V8__row_level_security.sql        the policies, and the role they apply to
```

| | Path | |
|---|---|---|
| `GET` | `/api/v1/applications` | `?status=&company=&includeArchived=&page=&size=&sort=` — `status` repeats: `?status=APPLIED&status=SCREEN` |
| `POST` | `/api/v1/applications` | 201 + `Location` header |
| `GET` | `/api/v1/applications/{id}` | |
| `PATCH` | `/api/v1/applications/{id}` | null fields mean "leave alone" |
| `POST` | `/api/v1/applications/{id}/status` | the only way status changes |
| `POST` | `/api/v1/applications/{id}/archive` | |
| `DELETE` | `/api/v1/applications/{id}` | really deletes; prefer archive |
| `GET` | `/api/v1/applications/{id}/events` | the timeline, newest first |
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
    ApplicationsTable.tsx   rows, the status control and the row menu
    ApplicationTimeline.tsx the history dialog
    CreateApplicationForm.tsx
    StatusBadge.tsx
    hooks.ts                queries, mutations, query keys
  components/
    ErrorNotice.tsx    whatever the API actually said
    RowMenu.tsx        the per-row `⋯` menu
    ConfirmDialog.tsx  used before anything irreversible
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

- **Archive and delete are not two strengths of the same button.** Archive means "this
  is over, stop showing it to me" - the row keeps its history and keeps counting towards
  the funnel stats. Delete means "this should never have existed": a typo, a duplicate.
  It cascades to `application_event` and takes the history with it. Exposed as equal
  choices, delete gets used for rejections because it feels more final, and the stats
  quietly rot - so archive is one click and delete needs a confirmation.
- **Friction matches reversibility, not severity.** That is the rule behind the row
  menu: the destructive item is last, separated, red, and asks first, while everything
  undoable acts immediately.
- **The row menu is a `popover`.** Not for novelty - the table sits inside
  `overflow-x-auto`, and an absolutely positioned dropdown inside a scroll container is
  clipped by it. The top layer is the only place the menu can render in full.
- **History is written in the same transaction as the change.** `application_event`
  rows are saved inside `changeStatus`, not after it, so there is no window where the
  status moved but the history did not. A history with gaps is worse than none, because
  it looks complete.
- **Who did it comes from the authenticated principal, behind `ActorContext`.** The
  service records an actor without knowing HTTP exists, which is what let phase 5c swap
  the implementation without touching a service method. Until then the answer came from
  an `X-Actor` header the API simply believed; now a session cookie means `HUMAN` and
  the service key means `AUTOMATION`, and the caller cannot choose. That mattered
  because misattributing a bot's write to a person corrupts the one column whose entire
  job is to be trustworthy.
- **The timeline is read-only.** An editable history answers "what do we currently claim
  happened", which is the question `job_application` already answers.

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

## Phase 5 — multi-user

Workspaces, Google sign-in, scoped queries and row-level security. It sits here
rather than at the end because `job_posting` and the Gmail job both get harder to
retrofit, and because phase 4's events table is what makes a shared workspace
legible: once two people can move the same application, "who moved this" stops
being a curiosity and becomes the only way to read the board.

5a-5d are merged: the tenancy tables, `workspace_id` on everything that holds data,
Google sign-in with a session cookie, and every application query scoped to the caller's
workspace. `X-Actor` is gone, replaced in one class exactly as `ActorContext` was
designed for. Reaching for another workspace's application is a 404, never a 403 - a 403
confirms the row exists, and a caller who can tell "no such id" from "not yours" can map
every application in the system without reading one.

5e adds row-level security, which is the same rule enforced a layer down. 5d makes the
scope part of every query, which is correct wherever somebody remembered to write it;
5e makes an unscoped query impossible, so an endpoint added next year is refused by
PostgreSQL rather than trusted by it. A `SELECT` with no workspace filter now returns
nothing rather than everything.

The policies are the small part. **The application no longer connects as the database
owner**, and that is the part worth carrying forward: PostgreSQL exempts superusers
unconditionally, and table owners unless the table is `FORCE`d, so policies written
against the role in `DB_USER` would have been inert while looking finished and passing
every test. So there are two roles now - `jobtracker` owns the schema and runs Flyway,
`jobtracker_app` owns nothing and serves requests. `V8` creates the second one, so a
`npm run db:reset` still needs no extra setup; `.env.example` says what the variables
are for.

Next is 5f: the Python worker's identity. It currently authenticates with one shared
key that acts across every workspace - the single most dangerous credential in the
project - and 5f replaces it with per-job identities and rotation. Phase 8 has its own
item from this phase: create the application role out of band, rather than letting a
migration set its password.

Two credentials now matter locally: the Google client from
[docs/google-oauth-setup.md](docs/google-oauth-setup.md), and a service key for the
Python worker, which has no browser and so no session. Both live in a gitignored
`.env`; `.env.example` says what to put there.

Then the scraper at 6, Gmail ingestion at 7 and deployment at 8. Sections 12 and 13
of the [blueprint](https://claude.ai/code/artifact/e244d427-b199-4c28-83c6-b5f85d882342)
have the reasoning and the seven-step plan.

### Not built yet, deliberately

- **Notes on your own status changes.** The API accepts a `note` on every status
  change and stores it, but only the Python worker sends one - the dropdown in the
  table has nowhere to type. Human events therefore have no "why". Worth adding the
  first time you want to explain one.
- **Un-archiving is not recorded.** `ApplicationEventType` has no value for it, so
  the timeline shows the archive but not the undo.
- **Field edits are not recorded.** A PATCH that halves the salary leaves no trace.
  That was a scope decision, not an oversight: it turns the timeline into an audit
  log, and most rows would be noise.
