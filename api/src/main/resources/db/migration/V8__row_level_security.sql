-- Phase 5e: make an unscoped query impossible instead of merely absent.
--
-- 5d put "AND workspace_id = ?" into every query the service issues, which is
-- correct everywhere somebody remembered to write it. That is the whole
-- weakness: it is a rule enforced by authorship. The endpoint added next year
-- by someone who has not read WorkspaceScopingTest is not covered by it, and
-- nothing fails until one tenant sees another tenant's applications.
--
-- Row-level security moves the same rule under the query, where forgetting is
-- not one of the options. After this migration a SELECT with no workspace
-- filter does not return everything - it returns nothing.
--
-- Read V7's header first; the workspace_id columns this file builds on, and the
-- reason application_event carries its own, were put there for this migration.


-- ---------------------------------------------------------------------------
-- 1. A role that RLS actually applies to.
-- ---------------------------------------------------------------------------

-- This is the part that makes 5e more than a few CREATE POLICY statements, and
-- the part that is easy to get wrong in a way that looks finished.
--
-- PostgreSQL exempts two kinds of caller from every policy: superusers, always
-- and unconditionally, and the table's owner unless the table is FORCEd. The
-- role this app connects as - jobtracker, from POSTGRES_USER - is both. Write
-- the policies, connect as that role, and every one of them is inert: the tests
-- pass, the SQL looks right, and nothing whatsoever is enforced. There is no
-- setting that makes a superuser obey a policy.
--
-- So tenancy needs a second role, and the split is the design:
--
--   jobtracker      owns the schema and runs Flyway. Bypasses policies, which
--                   is what lets a future migration backfill every row.
--   jobtracker_app  owns nothing, runs the application. Subject to policies.
--
-- Idempotent because roles live in the cluster, not the database: db:reset
-- drops the volume and takes the database with it, but a cluster that outlives
-- one database would already have this role.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jobtracker_app') THEN
        CREATE ROLE jobtracker_app LOGIN PASSWORD '${appDbPassword}';
    ELSE
        ALTER ROLE jobtracker_app LOGIN PASSWORD '${appDbPassword}';
    END IF;
END
$$;

-- The password arrives as a Flyway placeholder rather than a literal, so the
-- committed file holds no credential. It is still interpolated into a statement
-- the server receives, so a cluster running with log_statement = 'all' would
-- have it in the log. Acceptable for a development database created by
-- docker-compose and destroyed by db:reset; phase 8 should create the
-- production role out of band and let this branch find it already existing.

GRANT USAGE ON SCHEMA public TO jobtracker_app;

-- DML only. No CREATE, no DDL, no ownership: the application changes rows, and
-- a role that can ALTER TABLE can also DISABLE ROW LEVEL SECURITY, which would
-- make everything below a suggestion.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    company, job_application, application_event,
    workspace, app_user, workspace_member, automation_run
TO jobtracker_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jobtracker_app;

-- Deliberately no ALTER DEFAULT PRIVILEGES. Granting the app role automatic
-- access to every future table sounds like a convenience and is a trap: a table
-- added in phase 6 would become readable the moment it exists, with no policy
-- on it and nothing to notice. Without the default grant, the first query
-- against a new table fails with "permission denied", which is a loud and
-- immediate reminder to decide whether that table is tenant-scoped. A missing
-- GRANT is a five-second fix; a table that quietly serves every tenant is not.


-- ---------------------------------------------------------------------------
-- 2. Which workspace is this connection allowed to see?
-- ---------------------------------------------------------------------------

-- The answer travels in a session variable the application sets per transaction
-- (SET LOCAL), because a policy has no access to the HTTP request and the
-- connection is shared: HikariCP hands the same physical connection to a
-- different user seconds later. LOCAL is what makes that safe - the value dies
-- at COMMIT, so a connection returned to the pool carries nothing with it.
--
-- This function exists because of a bug caught in a spike before it reached
-- this file. The obvious policy body is
--
--     current_setting('app.workspace_id', true) = 'all'
--     OR workspace_id = current_setting('app.workspace_id', true)::bigint
--
-- and it fails, because SQL's OR does not short-circuit. PostgreSQL is free to
-- evaluate the cast even when the left side is already true, so the worker's
-- 'all' raises "invalid input syntax for type bigint". Moving the parse into a
-- function that returns NULL rather than throwing removes the failure mode,
-- instead of relying on an evaluation order the planner never promised.
--
-- NULL is also the right answer for "unset" and for "nonsense", and it is what
-- makes this fail closed: workspace_id = NULL is NULL, a policy that is not
-- TRUE denies, so a connection that never set the variable sees zero rows
-- rather than all of them. That is the most important line in this migration.
--
-- STABLE, so the planner evaluates it once per statement rather than per row.
-- search_path pinned to pg_catalog so nothing a caller creates can shadow the
-- operators this body depends on.
CREATE OR REPLACE FUNCTION app_current_workspace() RETURNS bigint
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog
    AS $$
        SELECT CASE
            WHEN current_setting('app.workspace_id', true) ~ '^[0-9]+$'
            THEN current_setting('app.workspace_id', true)::bigint
        END
    $$;

COMMENT ON FUNCTION app_current_workspace() IS
    'The workspace of the current transaction, or NULL when unset or not a number. NULL denies.';


-- ---------------------------------------------------------------------------
-- 3. The policies.
-- ---------------------------------------------------------------------------

-- ENABLE, not FORCE, and that is a decision rather than an omission.
--
-- FORCE would subject the table's owner to its own policies. It buys nothing
-- here - the owner is a superuser, and superusers bypass RLS whatever FORCE
-- says - and it actively breaks the case it appears to protect: once phase 8
-- gives production a non-superuser migration role, a FORCEd table would filter
-- that role too, and a migration written to backfill every row would silently
-- update none. A migration that touches nothing and reports success is a worse
-- failure than one that is refused outright.
ALTER TABLE company           ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_application   ENABLE ROW LEVEL SECURITY;
ALTER TABLE application_event ENABLE ROW LEVEL SECURITY;

-- USING governs which existing rows are visible to SELECT, UPDATE and DELETE.
-- WITH CHECK governs which rows may be written by INSERT and UPDATE. They are
-- deliberately not the same expression:
--
--   USING accepts 'all', because the Python worker legitimately scans every
--   workspace - readScope() returns empty for exactly that caller and nothing
--   else. It is the one honest unscoped read in the system.
--
--   WITH CHECK does not. A worker that may read everywhere must still name the
--   workspace it writes into; "write into all of them" is not a coherent
--   request. This is the SQL-level twin of WorkspaceContext, where readScope()
--   returns an Optional and currentId() throws for that same caller.
--
-- Giving both clauses the same body is the easy mistake, and it would let the
-- service key insert rows into a workspace nobody chose.
CREATE POLICY workspace_isolation ON company
    USING (current_setting('app.workspace_id', true) = 'all'
           OR workspace_id = app_current_workspace())
    WITH CHECK (workspace_id = app_current_workspace());

CREATE POLICY workspace_isolation ON job_application
    USING (current_setting('app.workspace_id', true) = 'all'
           OR workspace_id = app_current_workspace())
    WITH CHECK (workspace_id = app_current_workspace());

CREATE POLICY workspace_isolation ON application_event
    USING (current_setting('app.workspace_id', true) = 'all'
           OR workspace_id = app_current_workspace())
    WITH CHECK (workspace_id = app_current_workspace());

-- The policy does not replace 5d's WHERE clause, and 5d has not become
-- redundant. The policy is applied as a filter above the query the application
-- wrote, so it is the application's own "workspace_id = ?" that still lets the
-- planner choose idx_job_application_workspace. Delete the Specification and
-- the rows returned would stay correct while every list query became a
-- sequential scan. Correctness from the policy, selectivity from the query.


-- ---------------------------------------------------------------------------
-- Not covered here, on purpose.
-- ---------------------------------------------------------------------------
--
-- workspace, app_user and workspace_member have no policies. Sign-in has to
-- find a user by google_sub BEFORE any workspace is known - that lookup is how
-- the workspace is discovered in the first place - so a policy keyed on the
-- current workspace would make signing in impossible. They are protected by the
-- application being the only thing holding the credential, which is a weaker
-- claim, stated honestly rather than papered over.
--
-- automation_run has no workspace_id at all; V7 explains why a run belongs to
-- the system rather than to a tenant.
