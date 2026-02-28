CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_name       VARCHAR(255) NOT NULL,
    normalized_name     VARCHAR(255),
    amount              DECIMAL(12,2),
    frequency           VARCHAR(50) CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUAL')),
    account_id          UUID REFERENCES accounts(id) ON DELETE SET NULL,
    first_seen_date     DATE,
    last_charged_date   DATE,
    next_expected_date  DATE,
    times_charged       INTEGER DEFAULT 1,
    annual_cost         DECIMAL(12,2),
    category            VARCHAR(100),
    is_active           BOOLEAN DEFAULT TRUE,
    is_duplicate        BOOLEAN DEFAULT FALSE,
    duplicate_of_id     UUID REFERENCES subscriptions(id),
    notes               TEXT,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_account_id ON subscriptions(account_id);
CREATE INDEX idx_subscriptions_is_active ON subscriptions(is_active);
CREATE INDEX idx_subscriptions_normalized_name ON subscriptions(normalized_name);
