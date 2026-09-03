# `automation/` — the Python workers

Phase 3. Scheduled jobs that read and write the tracker **through the API**, never
through the database.

That restriction is the entire design. `nudge_stale` could reach Postgres directly
in about fifteen lines — and it would then be a second place where the status
lifecycle is decided, a second thing to update when a column moves, and a way to
write a row Spring would have rejected. Going through HTTP costs a few
milliseconds and buys one system of record.

## Set up

From this directory, once:

```bash
py -m venv .venv
```

```bash
.venv/Scripts/python.exe -m pip install -e ".[dev]"
```

`-e` is an editable install: the package is importable from anywhere while still
being the files in `src/`, so edits take effect with no reinstall.

Copy `.env.example` to `.env` if you want to change any defaults. Nothing here
needs it to run against a local API.

## Run a job

The API must be up (`npm run dev:api` from the repo root).

```bash
npm run automation
```

That is the scan: it reports and changes nothing. Longer form, with everything
spelled out:

```bash
cd automation && .venv/Scripts/python.exe -m jobtracker_automation nudge-stale --stale-after-days 21
```

To actually act on the findings, both flags are required:

```bash
cd automation && .venv/Scripts/python.exe -m jobtracker_automation nudge-stale --ghost-after-days 45 --apply
```

| Flag | |
|---|---|
| `--stale-after-days N` | silence before an application is reported. Default 14 |
| `--ghost-after-days M` | silence before it *would* be marked GHOSTED. Must be ≥ N |
| `--apply` | actually move them. Without it, nothing is written |
| `--trigger schedule` | records the run as scheduled rather than manual |
| `--no-record` | skip the `automation_run` row entirely |
| `-v` | log every request |

Exit codes, because a scheduler reads nothing else: **0** ran, **1** the API
refused or was unreachable, **2** the flags were incoherent.

## Test

```bash
npm run automation:test
```

31 tests, none of which need a database. The last eight need the API running and
skip themselves when it is not — see the contract section below.

## What `nudge_stale` does

Walks every non-archived application and reports the ones that have gone quiet:
applied to (`appliedAt` is set), still open, and untouched for N days.

The interesting part is what it refuses to decide for itself.

**It has no copy of the lifecycle.** "Still open" is not a list of statuses kept
in Python — it is `allowedNextStatuses` coming back empty. Before ghosting
anything it asks the same field whether `GHOSTED` is a legal next step. The rules
live once, in the Java enum, exactly as they do for the React dropdown.

**It reads by default.** A scan records a run and touches nothing. This is not
timidity: `updated_at` is the only staleness signal the API offers, so *a job that
writes to an application erases that application's own staleness*. Writing has to
be worth it, and asked for.

**Idempotency is structural, not bookkept.** Ghosting is safe to re-run because
`GHOSTED` is terminal, so the next run's selection filters those rows out before
the write path sees them. There is no "already processed" flag to keep in sync —
the state machine already answers the question.

**A conflict is not a failure.** If someone moves an application between the read
and the write, the API returns 409 and the job records a skip and carries on.
Their change is newer and better informed. Any other error status does fail the
run: a 400 means the worker is wrong, and papering over that is how a broken job
runs quietly for a month.

## How a run is recorded

Two calls, not one: `POST /automation/runs` before the work, then
`POST /automation/runs/{id}/complete` after it, wrapped in a context manager so
`try/finally` guarantees the second one.

One call at the end would be simpler and would lose the only case worth
recording. A job that is killed, times out, or dies where its own error handling
cannot reach writes nothing at all — and "nothing" looks exactly like "never
scheduled". With two calls it leaves a row stuck in `RUNNING` with a start time,
which is a visible, diagnosable state. The database enforces the pairing:
`CHECK ((status = 'RUNNING') = (finished_at IS NULL))`.

`items_affected` counts what the job **changed**, never what it found. A dry run
that spots nine stale applications reports nine in `details` and zero affected.

If completing the run fails too — likely, since a failing API is why the job died
— the reporting error is logged and dropped. The original exception is the one
that propagates. An error raised while reporting an error must never replace it.

## Keeping the models honest

`models.py` mirrors the Java DTOs by hand. The front end does this differently:
`web/src/api/schema.d.ts` is generated from `/v3/api-docs`, and `tsc` then names
every line that a backend change broke.

The tradeoff behind the difference: the front end consumes nearly the whole API
surface and benefits from regenerating wholesale, while a worker touches five
endpoints and gains more from models that carry a little behaviour
(`is_terminal`, `last_movement`, `label()`). Hand-written models with no check
would just be drift waiting to happen, so `tests/test_contract.py` reads the live
OpenAPI document and asserts, per model:

- every field the API sends exists here — a field added in Java and ignored in
  Python is silent data loss
- every field marked `requiredMode = REQUIRED` is non-optional here — otherwise
  every job carries `None` checks for cases that cannot happen
- every enum matches in **both** directions — a value Python does not know is a
  validation crash mid-run; a value the API does not know is a 400 waiting to
  happen

Those tests skip when the API is not running, so `pytest` still passes offline.
Run them for real after any backend change. If `datamodel-code-generator` later
earns its place, that decision is now a small one.

## Structure

```
automation/
  pyproject.toml            deps, entry point, pytest config
  src/jobtracker_automation/
    config.py               pydantic-settings; CLI > env > .env > default
    models.py               pydantic mirrors of the DTOs
    client.py               httpx client, paging, ApiError
    runs.py                 the start/complete context manager
    jobs/nudge_stale.py     the job, with select_stale as a pure function
    cli.py                  argparse, logging, exit codes
  tests/
```

Three details in `client.py` worth knowing before writing a second job:

- **Retries cover connection errors only.** If the connection never opened,
  nothing can have happened twice. Retrying a 5xx is a different promise: a POST
  that timed out may well have been applied, and this API has no idempotency
  keys to make a repeat safe. So 5xx fails the run and the next one picks it up.
- **Paging is pinned to `sort=id,asc`.** The API's default sort is `createdAt`,
  and any job that writes moves `updatedAt`; paging over a mutable ordering can
  show a row twice or skip one entirely as rows shift between pages.
- **Every request carries `X-Service-Key`, plus the job name.** Both are set once
  on the httpx session, not per call, so a new endpoint on the client cannot
  forget them. The key is how this worker gets in at all: from phase 5c the API
  requires a principal on every request, and this one has no browser and so no
  session cookie. Set `JOBTRACKER_SERVICE_KEY` to the same value the server reads
  from `AUTOMATION_SERVICE_KEY`. A missing key raises `MissingServiceKey` at
  construction and exits 2 — before a run is recorded, rather than as a wall of
  401s inside one.
- **`X-Actor` is gone, and its absence is the point.** The worker used to declare
  itself a bot with that header and the API believed it, which meant a browser
  could declare the same thing. `AUTOMATION` is now inferred from having
  authenticated with the service key. Only the job name still has to be told,
  because only the worker knows it: pass `job_name=` when constructing the
  client; a job that forgets loses the name, not the identity.

## Next

`nudge_stale` deliberately ignores applications that were never applied to —
saved months ago and forgotten. That is a real backlog, but it is the user's own
inaction rather than someone else's silence, so it wants its own job and its own
threshold rather than a second meaning bolted onto this one.
