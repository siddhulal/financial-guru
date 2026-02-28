CREATE TABLE account_balance_snapshots (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    balance       DECIMAL(14,2) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_balance_snapshots_account_date
    ON account_balance_snapshots(account_id, snapshot_date);
CREATE INDEX idx_balance_snapshots_date ON account_balance_snapshots(snapshot_date DESC);
