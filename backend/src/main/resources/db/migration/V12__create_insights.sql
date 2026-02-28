CREATE TABLE insights (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          VARCHAR(50) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT NOT NULL,
    action_text   VARCHAR(500),
    impact_amount DECIMAL(12,2),
    severity      VARCHAR(20) NOT NULL,
    merchant_name VARCHAR(255),
    category      VARCHAR(100),
    is_dismissed  BOOLEAN DEFAULT FALSE,
    generated_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_insights_is_dismissed ON insights(is_dismissed) WHERE is_dismissed = FALSE;
CREATE INDEX idx_insights_generated_at ON insights(generated_at DESC);
