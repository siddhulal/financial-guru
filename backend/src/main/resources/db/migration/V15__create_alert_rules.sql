CREATE TABLE alert_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    rule_type           VARCHAR(50) NOT NULL CHECK (rule_type IN (
                            'TRANSACTION_AMOUNT','MONTHLY_CATEGORY_SPEND',
                            'BALANCE_BELOW','UTILIZATION_ABOVE')),
    condition_operator  VARCHAR(20) NOT NULL DEFAULT 'GREATER_THAN',
    threshold_amount    DECIMAL(12,2) NOT NULL,
    category            VARCHAR(100),
    account_id          UUID REFERENCES accounts(id) ON DELETE CASCADE,
    is_active           BOOLEAN DEFAULT TRUE,
    last_triggered_at   TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
