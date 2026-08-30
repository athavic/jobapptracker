-- Phase 0/1: just the two tables the CRUD slice needs.
-- Every later change is a NEW file (V2__..., V3__...). Never edit an applied migration.

CREATE TABLE company (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    website     VARCHAR(500),
    careers_url VARCHAR(500),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_name UNIQUE (name)
);

CREATE TABLE job_application (
    id             BIGSERIAL    PRIMARY KEY,
    company_id     BIGINT       NOT NULL REFERENCES company (id) ON DELETE RESTRICT,
    role_title     VARCHAR(250) NOT NULL,
    -- stored as text, not an int: reordering the Java enum must never change
    -- the meaning of rows already in the table
    status         VARCHAR(32)  NOT NULL,
    source         VARCHAR(64),
    job_url        VARCHAR(1000),
    location       VARCHAR(200),
    remote_type    VARCHAR(16),
    salary_min     INTEGER,
    salary_max     INTEGER,
    currency       VARCHAR(3),
    priority       INTEGER      NOT NULL DEFAULT 3,
    resume_version VARCHAR(100),
    notes          TEXT,
    -- TIMESTAMPTZ everywhere. Store UTC, convert at the edges.
    applied_at     TIMESTAMPTZ,
    archived       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    CONSTRAINT ck_priority
        CHECK (priority BETWEEN 1 AND 5)
);

CREATE INDEX idx_job_application_status     ON job_application (status);
CREATE INDEX idx_job_application_company    ON job_application (company_id);
CREATE INDEX idx_job_application_applied_at ON job_application (applied_at DESC NULLS LAST);
