CREATE TABLE savings_goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'OTHER',
    target_amount   DECIMAL(14,2) NOT NULL,
    current_amount  DECIMAL(14,2) NOT NULL DEFAULT 0,
    target_date     DATE,
    linked_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
    color           VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
