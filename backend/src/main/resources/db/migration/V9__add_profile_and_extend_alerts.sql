ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_type_check;
ALTER TABLE alerts ADD CONSTRAINT alerts_type_check CHECK (type IN (
    'DUE_DATE','APR_EXPIRY','DUPLICATE_CHARGE','ANOMALY','SUBSCRIPTION',
    'HIGH_UTILIZATION','OVERCHARGE','UNUSUAL_MERCHANT','LARGE_TRANSACTION',
    'BUDGET_WARNING','BUDGET_EXCEEDED'
));

CREATE TABLE financial_profile (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monthly_income               DECIMAL(12,2),
    income_source                VARCHAR(20) DEFAULT 'MANUAL',
    pay_frequency                VARCHAR(20) DEFAULT 'MONTHLY',
    emergency_fund_target_months INTEGER DEFAULT 6,
    notes                        TEXT,
    created_at                   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at                   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_financial_profile_singleton ON financial_profile ((TRUE));
