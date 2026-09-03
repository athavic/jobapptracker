-- Phase 5a: the four tables multi-user needs, and nothing that reads them.
--
-- Deliberately schema-only. No entity maps these, no endpoint touches them, and
-- ddl-auto: validate does not object to a table no entity claims - so this file
-- cannot break the running app. That is the whole point of doing it first: every
-- later sitting in phase 5 is harder to undo than this one, and the shape is
-- only free to change while nothing depends on it.
--
-- The tenant is the WORKSPACE, never the user. A workspace with one member is
-- how a solo account looks, so there is no second code path to keep working when
-- collaboration arrives - the multi-tenant path is the only path from the start.
-- Keying on a user id instead works perfectly right up until two people need the
-- same board, and by then every row in the database has the wrong owner column.

-- Named app_user, not user: "user" is a reserved word in PostgreSQL, and a table
-- you can only ever address as "user" with quotes is a papercut forever.
CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,

    -- Google's 'sub' claim: opaque, and permanent for that account. This is the
    -- identity. The email is NOT, and the distinction is load-bearing - on a
    -- Workspace domain an address can be reassigned to a new person after the
    -- original leaves, so matching on email hands a new hire their predecessor's
    -- applications. Store the email, never key on it.
    google_sub    VARCHAR(255) NOT NULL,

    -- Kept because invitations are addressed to it and the UI shows it, but
    -- treated as a mutable attribute throughout. Deliberately NOT unique: the
    -- reassignment case above is exactly when two rows legitimately share an
    -- address, and a unique constraint here would lock the new person out.
    email         VARCHAR(320) NOT NULL,

    display_name  VARCHAR(200),
    avatar_url    VARCHAR(500),

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_google_sub UNIQUE (google_sub)
);

-- Sign-in matches an invitation by address, and addresses are not case sensitive
-- in practice even where the RFC allows it. Indexing lower(email) rather than
-- email is what makes that lookup both correct and fast; see the note on
-- uq_company_name below for what happens when the two disagree.
CREATE INDEX idx_app_user_email ON app_user (lower(email));


CREATE TABLE workspace (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- No owner_id column on purpose. Ownership is a role on workspace_member, and
-- storing it in both places creates a state where they disagree - at which point
-- you have to decide which one is lying. One representation, queried when needed.


CREATE TABLE workspace_member (
    id           BIGSERIAL   PRIMARY KEY,

    workspace_id BIGINT      NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    -- CASCADE on both sides: a membership describes a relationship, and it is
    -- meaningless once either end is gone. Contrast job_application.company_id,
    -- which is RESTRICT because an application without its company is data loss.
    app_user_id  BIGINT      NOT NULL REFERENCES app_user (id)  ON DELETE CASCADE,

    -- Three roles, kept small until a fourth earns itself. OWNER can remove
    -- members and delete the workspace, MEMBER can change applications, VIEWER
    -- can only read. Same VARCHAR + CHECK pattern as every other enum here, so
    -- reordering the Java enum can never change what a stored row means.
    role         VARCHAR(16) NOT NULL,

    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_workspace_member_role
        CHECK (role IN ('OWNER', 'MEMBER', 'VIEWER')),

    -- One membership per person per workspace. Without this, "add member" run
    -- twice gives someone two roles and the answer to "what may they do?"
    -- depends on which row you read first.
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, app_user_id)
);

-- One rule this schema deliberately does NOT enforce: a workspace must always
-- keep at least one OWNER.
--
-- It is not expressible as a constraint. A CHECK asks "is this row valid?",
-- while this asks "does removing this row leave the workspace ownerless?" -
-- a question about a transition, which needs the other rows at the moment of
-- the change. So it lives in the service layer, on the remove-member and
-- change-role paths, with SELECT ... FOR UPDATE on the workspace row before
-- counting owners: two owners demoting each other at once would otherwise both
-- see a second owner still standing and both succeed.
--
-- Chosen over a trigger because the caller needs a 409 carrying a sentence a
-- person can read, and a trigger raises an exception you would translate into
-- that sentence anyway - so the message gets written either way, and the
-- trigger only adds a second place the rule hides. Written down here so these
-- constraints are not mistaken for the whole story.

-- "Which workspaces am I in?" runs on essentially every authenticated request
-- from 5d onward. The UNIQUE constraint above already indexes
-- (workspace_id, app_user_id), which cannot serve a lookup by user alone -
-- a composite index only helps queries that constrain its leading column.
CREATE INDEX idx_workspace_member_user ON workspace_member (app_user_id);


CREATE TABLE workspace_invite (
    id             BIGSERIAL    PRIMARY KEY,

    workspace_id   BIGINT       NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,

    -- Who it was sent to, and what they get on acceptance. The role is fixed at
    -- invite time rather than chosen at accept time, so the person accepting
    -- cannot pick their own permissions.
    email          VARCHAR(320) NOT NULL,
    role           VARCHAR(16)  NOT NULL,

    -- The SHA-256 of the invite token, never the token itself.
    --
    -- The token in the emailed link is a bearer credential: whoever holds it can
    -- join this workspace. Storing it in plain text means anyone who can read
    -- this table - a backup, a support query, a leaked dump - can accept every
    -- outstanding invitation. Storing the hash means the link still works (hash
    -- what arrives, compare) while the table itself grants nothing. The raw
    -- token exists only in the email, and only until it expires.
    token_hash     VARCHAR(64)  NOT NULL,

    -- SET NULL rather than CASCADE: an invitation does not stop having happened
    -- because the person who sent it deleted their account, and the row is still
    -- the audit trail for how someone got access.
    invited_by     BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,

    expires_at     TIMESTAMPTZ  NOT NULL,

    -- Null exactly while the invitation is outstanding. Recording who accepted
    -- separately from who it was addressed to, because an alias or a forwarded
    -- mail makes those genuinely different questions.
    accepted_at    TIMESTAMPTZ,
    accepted_by    BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_workspace_invite_role
        CHECK (role IN ('OWNER', 'MEMBER', 'VIEWER')),

    -- An invitation that expires before it is sent is a bug, not a fast expiry.
    CONSTRAINT ck_workspace_invite_expiry
        CHECK (expires_at > created_at),

    -- accepted_at and accepted_by move together or not at all, the same way
    -- automation_run ties status to finished_at. Half an acceptance is a row
    -- that says someone joined without saying who.
    CONSTRAINT ck_workspace_invite_accepted
        CHECK ((accepted_at IS NULL) = (accepted_by IS NULL)),

    CONSTRAINT uq_workspace_invite_token UNIQUE (token_hash)
);

-- At most one OUTSTANDING invitation per address per workspace. A partial index
-- rather than a plain UNIQUE, because the constraint only applies while the
-- invite is pending: re-inviting someone who accepted and later left has to stay
-- possible, and their old accepted row must not block it.
CREATE UNIQUE INDEX uq_workspace_invite_pending
    ON workspace_invite (workspace_id, lower(email))
    WHERE accepted_at IS NULL;

-- "Show this workspace's outstanding invitations" - the 5g members screen.
CREATE INDEX idx_workspace_invite_workspace ON workspace_invite (workspace_id);


-- A note for 5b, recorded here because this is where the question came up.
--
-- uq_company_name is UNIQUE (name) - case SENSITIVE - while the code that reads
-- it is findByNameIgnoreCase. The two disagree: 'Stripe' and 'stripe' both
-- satisfy the constraint, and a lookup that then matches both rows fails on a
-- method declared to return one. Only reachable today through the check-then-
-- insert race in findOrCreateCompany, which is why it has not bitten.
--
-- 5b has to rewrite this constraint anyway, to UNIQUE (workspace_id, name) -
-- two workspaces both tracking Stripe is normal, not a conflict. Make it
-- UNIQUE (workspace_id, lower(name)) at the same time and the disagreement goes
-- away as a side effect of a migration that was already necessary.
