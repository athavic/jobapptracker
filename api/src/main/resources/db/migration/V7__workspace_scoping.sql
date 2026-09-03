-- Phase 5b: give the tables that already hold data a workspace to belong to.
--
-- V6 added the tenancy tables and nothing that reads them. This is the file
-- that actually touches your applications, and the ordering inside it is the
-- whole difficulty:
--
--   1. add the column NULLABLE
--   2. create a workspace for the rows that exist
--   3. backfill every row to it
--   4. only THEN set NOT NULL and add the foreign keys
--
-- Adding a NOT NULL column straight away fails on the first existing row -
-- PostgreSQL has no value to put there and no way to guess one. The four steps
-- are one migration because a database left between them is a database where
-- writes fail: nullable-but-unbackfilled is not a state worth being able to
-- stop in.

-- ---------------------------------------------------------------------------
-- 1. The columns, nullable for now.
-- ---------------------------------------------------------------------------

ALTER TABLE company           ADD COLUMN workspace_id BIGINT;
ALTER TABLE job_application   ADD COLUMN workspace_id BIGINT;

-- application_event gets the column too, even though its workspace is already
-- reachable through application_id. That looks like redundancy and is not:
-- phase 5e puts row-level security policies on these tables, and a policy that
-- has to join another table to discover its tenant is both slower and easier to
-- get wrong. Every table a policy protects wants the tenant sitting on it.
ALTER TABLE application_event ADD COLUMN workspace_id BIGINT;


-- ---------------------------------------------------------------------------
-- 2. Somewhere for the existing rows to go.
-- ---------------------------------------------------------------------------

-- The id is written explicitly rather than left to the sequence, and that is
-- deliberate. Until phase 5c there is no sign-in and therefore no principal to
-- ask which workspace a request belongs to, so the API has to name one. A
-- constant that is true by luck - "the sequence starts at 1, so it will be 1" -
-- is the kind of assumption that survives until someone reseeds a database.
-- Writing the id here makes app.tenancy.bootstrap-workspace-id a fact about
-- this migration instead.
--
-- setval then advances the sequence past it, or the next workspace created
-- through the API would collide with this row.
INSERT INTO workspace (id, name) VALUES (1, 'Personal');
SELECT setval(pg_get_serial_sequence('workspace', 'id'), 1, true);

-- No workspace_member row. This workspace deliberately has no owner yet:
-- app_user is empty until the first Google sign-in exists to populate it, and
-- inventing a user row here would mean inventing a google_sub, which is the one
-- column in that table that must never hold something made up. Phase 5c adopts
-- this workspace on first sign-in instead.


-- ---------------------------------------------------------------------------
-- 3. Backfill.
-- ---------------------------------------------------------------------------

UPDATE company           SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE job_application   SET workspace_id = 1 WHERE workspace_id IS NULL;
UPDATE application_event SET workspace_id = 1 WHERE workspace_id IS NULL;


-- ---------------------------------------------------------------------------
-- 4. Now the column can mean what it is supposed to mean.
-- ---------------------------------------------------------------------------

ALTER TABLE company           ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE job_application   ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE application_event ALTER COLUMN workspace_id SET NOT NULL;

-- CASCADE on all three: deleting a workspace means deleting the workspace's
-- data, and a job application whose workspace is gone is not recoverable by
-- anyone. Note this is a different judgement from job_application.company_id,
-- which stays RESTRICT - losing a company would orphan applications that are
-- still perfectly valid, whereas losing a workspace takes the applications with
-- it by definition.
ALTER TABLE company
    ADD CONSTRAINT fk_company_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE;

ALTER TABLE job_application
    ADD CONSTRAINT fk_job_application_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE;

ALTER TABLE application_event
    ADD CONSTRAINT fk_application_event_workspace
    FOREIGN KEY (workspace_id) REFERENCES workspace (id) ON DELETE CASCADE;


-- ---------------------------------------------------------------------------
-- 5. The company uniqueness rule, corrected on the way past.
-- ---------------------------------------------------------------------------

-- uq_company_name was UNIQUE (name), and it disagreed with the code that read
-- it. findOrCreateCompany looks companies up with findByNameIgnoreCase, so
-- 'Stripe' and 'stripe' were the same company to Java and two different rows to
-- PostgreSQL - a lookup that then matched both would fail on a method declared
-- to return one. Only reachable through the check-then-insert race, which is
-- why it never bit.
--
-- The constraint has to be rewritten here regardless: two workspaces both
-- tracking Stripe is normal, not a conflict, so global uniqueness on name is
-- now simply wrong. Scoping it and fixing the case disagreement is the same
-- edit, so the bug goes away as a side effect of a migration that was already
-- necessary.
--
-- A UNIQUE INDEX rather than a table constraint because PostgreSQL constraints
-- cannot be built on an expression like lower(name).
ALTER TABLE company DROP CONSTRAINT uq_company_name;

CREATE UNIQUE INDEX uq_company_workspace_name
    ON company (workspace_id, lower(name));


-- ---------------------------------------------------------------------------
-- 6. Indexes.
-- ---------------------------------------------------------------------------

-- From 5d onward every application query carries "AND workspace_id = ?", so
-- this is the column that narrows first.
--
-- Deliberately not composite yet. The obvious candidates - (workspace_id,
-- status), (workspace_id, applied_at DESC) - depend on query shapes that 5d has
-- not settled, and an index chosen before the query it serves is a guess that
-- costs write throughput forever. Revisit with EXPLAIN once the base
-- Specification exists.
CREATE INDEX idx_job_application_workspace ON job_application (workspace_id);

-- company is already covered by uq_company_workspace_name above, which leads
-- with workspace_id and therefore serves a lookup by workspace alone.
--
-- application_event gets no workspace index on purpose. Its only query is
-- "one application's events, newest first", which idx_application_event_
-- application already serves through a far more selective column; adding
-- workspace_id would be a second index the planner never chooses.


-- ---------------------------------------------------------------------------
-- Still global, and correctly so.
-- ---------------------------------------------------------------------------
--
-- automation_run has no workspace_id. A run of nudge_stale is one execution of
-- one job, and from 5f the worker acts across every workspace at once - so the
-- run belongs to the system, not to a tenant. What the run TOUCHED is already
-- recorded per-workspace, as application_event rows. Give the run a workspace
-- and you would have to either invent one or write the same run several times.
