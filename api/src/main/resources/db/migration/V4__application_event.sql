-- Phase 4: one row per meaningful thing that happened to an application.
--
-- This table exists because phase 3 created a question the schema could not
-- answer. Once a Python job can move an application to GHOSTED, a ghosted row
-- looks exactly like one you ghosted yourself - job_application stores the
-- current state and nothing about how it got there. The events table is the
-- history that state alone throws away.
--
-- Rows are written inside the same transaction as the change they describe, so
-- there is no window where the status moved but the history did not.

CREATE TABLE application_event (
    id             BIGSERIAL   PRIMARY KEY,

    -- ON DELETE CASCADE: DELETE /applications/{id} really deletes, and an event
    -- pointing at an application that no longer exists is not history, it is a
    -- dangling row. Archive is the soft option and leaves events untouched.
    application_id BIGINT      NOT NULL
        REFERENCES job_application (id) ON DELETE CASCADE,

    type           VARCHAR(32) NOT NULL,

    -- Both statuses are stored, not just the new one. Reconstructing "from" by
    -- looking at the previous row works right up until a row is missing, and a
    -- history you cannot trust is worse than none.
    from_status    VARCHAR(32),
    to_status      VARCHAR(32),

    -- Who. Until phase 5 there is no authentication, so this is what the caller
    -- claimed via X-Actor rather than something proven.
    actor          VARCHAR(16) NOT NULL,
    -- Which one, when the actor is a machine: 'nudge_stale'. Free text for the
    -- same reason automation_run.job_name is - jobs are code, not data.
    actor_detail   VARCHAR(64),

    note           TEXT,

    -- Named occurred_at, not created_at: a backfilled or imported event describes
    -- a moment that is not the moment the row was inserted. Keeping the two ideas
    -- in one column is how timelines end up lying.
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_application_event_type
        CHECK (type IN ('CREATED', 'STATUS_CHANGED', 'ARCHIVED')),
    CONSTRAINT ck_application_event_actor
        CHECK (actor IN ('HUMAN', 'AUTOMATION', 'SYSTEM')),

    -- The enum alone cannot say "a STATUS_CHANGED must know what it changed to".
    -- These two say it, so a bug in the service is rejected by the database
    -- rather than quietly stored as an event that explains nothing.
    CONSTRAINT ck_application_event_to_status
        CHECK ((type IN ('CREATED', 'STATUS_CHANGED')) = (to_status IS NOT NULL)),
    CONSTRAINT ck_application_event_from_status
        CHECK ((type = 'STATUS_CHANGED') = (from_status IS NOT NULL))
);

-- The only query the timeline makes: one application's events, newest first.
CREATE INDEX idx_application_event_application
    ON application_event (application_id, occurred_at DESC);

-- Applications that predate this table would otherwise show an empty timeline,
-- which reads as "nothing ever happened" rather than "we were not recording".
-- created_at is a real timestamp and the row genuinely was created by hand, so
-- this is a reconstruction, not an invention. Later phases have no equivalent
-- excuse: never backfill history you cannot derive from data you already trust.
--
-- Two deliberate choices about honesty here. The actor is SYSTEM, not HUMAN:
-- this row was written by a migration, and claiming you wrote it would make the
-- one column that records provenance the first column to lie. And to_status is
-- the CURRENT status, because the original is genuinely unknowable - the note
-- says so, so nobody reads it later as an observation.
INSERT INTO application_event (application_id, type, to_status, actor, note, occurred_at)
SELECT id,
       'CREATED',
       status,
       'SYSTEM',
       'Reconstructed when event history was introduced; status shown is the current one.',
       created_at
FROM job_application;
