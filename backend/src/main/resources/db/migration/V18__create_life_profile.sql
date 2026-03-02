-- Life OS: life_profile and life_guidance tables

CREATE TABLE life_profile (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name             VARCHAR(100),
    birth_year             INTEGER,
    city                   VARCHAR(100),
    state                  VARCHAR(50),
    country                VARCHAR(50) DEFAULT 'US',
    job_title              VARCHAR(200),
    company                VARCHAR(200),
    industry               VARCHAR(100),
    employment_type        VARCHAR(50) DEFAULT 'FULL_TIME',
    years_at_current_job   INTEGER,
    total_years_experience INTEGER,
    annual_salary          DECIMAL(14,2),
    annual_bonus           DECIMAL(14,2),
    equity_annual_value    DECIMAL(14,2),
    skills                 TEXT,
    is_married             BOOLEAN DEFAULT FALSE,
    spouse_employed        BOOLEAN DEFAULT FALSE,
    spouse_job_title       VARCHAR(200),
    spouse_annual_income   DECIMAL(14,2),
    number_of_kids         INTEGER DEFAULT 0,
    kids_ages              TEXT,
    notes                  TEXT,
    created_at             TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_life_profile_singleton ON life_profile ((TRUE));

CREATE TABLE life_guidance (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guidance_type VARCHAR(50) NOT NULL,
    title         VARCHAR(500) NOT NULL,
    content       TEXT NOT NULL,
    action_items  TEXT,
    source        VARCHAR(20) DEFAULT 'AI',
    is_dismissed  BOOLEAN DEFAULT FALSE,
    generated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_life_guidance_recent ON life_guidance(guidance_type, generated_at DESC);
