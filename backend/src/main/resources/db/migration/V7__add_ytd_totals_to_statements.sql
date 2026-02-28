ALTER TABLE statements
    ADD COLUMN IF NOT EXISTS ytd_total_fees      DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS ytd_total_interest  DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS ytd_year            INTEGER;
