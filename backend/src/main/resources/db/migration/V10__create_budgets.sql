CREATE TABLE budgets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category       VARCHAR(100) NOT NULL,
    monthly_limit  DECIMAL(12,2) NOT NULL CHECK (monthly_limit > 0),
    is_active      BOOLEAN DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_budgets_category ON budgets(category);
