ALTER TABLE financial_profile
    ADD COLUMN IF NOT EXISTS age                   INTEGER,
    ADD COLUMN IF NOT EXISTS target_retirement_age INTEGER DEFAULT 65,
    ADD COLUMN IF NOT EXISTS current_investments   DECIMAL(14,2) DEFAULT 0;
