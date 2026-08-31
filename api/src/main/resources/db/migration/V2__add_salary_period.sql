-- Hourly compensation can include cents, so salary amounts can no longer be integers.
ALTER TABLE job_application
    ALTER COLUMN salary_min TYPE NUMERIC(12, 2) USING salary_min::numeric,
    ALTER COLUMN salary_max TYPE NUMERIC(12, 2) USING salary_max::numeric,
    ADD COLUMN salary_period VARCHAR(16);

-- Every salary recorded before this migration was displayed as an annual salary.
UPDATE job_application
SET salary_period = 'ANNUAL'
WHERE salary_min IS NOT NULL OR salary_max IS NOT NULL;

ALTER TABLE job_application
    ADD CONSTRAINT ck_salary_period
        CHECK (salary_period IS NULL OR salary_period IN ('ANNUAL', 'HOURLY'));
