-- Phase 3: every automation job execution leaves a row here.
--
-- The point is not logging - logs are already in stdout. The point is that the
-- dashboard can answer "when did nudge_stale last run, and did it work?" without
-- shelling into anything, and that a job which dies mid-run leaves a visible
-- RUNNING row rather than no row at all.

CREATE TABLE automation_run (
    id             BIGSERIAL   PRIMARY KEY,
    -- The job's stable identifier, e.g. 'nudge_stale'. Not a foreign key: jobs
    -- are code, not data, and a renamed job must not orphan its history.
    job_name       VARCHAR(64) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    items_scanned  INTEGER     NOT NULL DEFAULT 0,
    items_affected INTEGER     NOT NULL DEFAULT 0,
    -- One human-readable line, and the structured payload behind it. jsonb rather
    -- than text so the shape of a job's output can change without a migration,
    -- and so it stays queryable (details -> 'stale' @> ...).
    message        TEXT,
    details        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_automation_run_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_automation_run_finished
        CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    CONSTRAINT ck_automation_run_counts
        CHECK (items_scanned >= 0 AND items_affected >= 0)
);

-- The dashboard's only real query: the latest runs of one job, newest first.
CREATE INDEX idx_automation_run_job_started
    ON automation_run (job_name, started_at DESC);
