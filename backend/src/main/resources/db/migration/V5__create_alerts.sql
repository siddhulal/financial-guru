CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(50) NOT NULL CHECK (type IN (
                        'DUE_DATE', 'APR_EXPIRY', 'DUPLICATE_CHARGE',
                        'ANOMALY', 'SUBSCRIPTION', 'HIGH_UTILIZATION',
                        'OVERCHARGE', 'UNUSUAL_MERCHANT', 'LARGE_TRANSACTION'
                    )),
    severity        VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    title           VARCHAR(255) NOT NULL,
    message         TEXT NOT NULL,
    ai_explanation  TEXT,
    account_id      UUID REFERENCES accounts(id) ON DELETE SET NULL,
    transaction_id  UUID REFERENCES transactions(id) ON DELETE SET NULL,
    is_read         BOOLEAN DEFAULT FALSE,
    is_resolved     BOOLEAN DEFAULT FALSE,
    resolved_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_alerts_type ON alerts(type);
CREATE INDEX idx_alerts_severity ON alerts(severity);
CREATE INDEX idx_alerts_is_read ON alerts(is_read) WHERE is_read = FALSE;
CREATE INDEX idx_alerts_is_resolved ON alerts(is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX idx_alerts_account_id ON alerts(account_id);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
