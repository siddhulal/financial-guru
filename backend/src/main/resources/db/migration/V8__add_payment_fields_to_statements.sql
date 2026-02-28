ALTER TABLE statements
    ADD COLUMN IF NOT EXISTS minimum_payment   DECIMAL(12,2),
    ADD COLUMN IF NOT EXISTS payment_due_date  DATE;
