CREATE TABLE statements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID REFERENCES accounts(id) ON DELETE SET NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    statement_month DATE,
    start_date      DATE,
    end_date        DATE,
    opening_balance DECIMAL(12,2),
    closing_balance DECIMAL(12,2),
    total_credits   DECIMAL(12,2),
    total_debits    DECIMAL(12,2),
    status          VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_statements_account_id ON statements(account_id);
CREATE INDEX idx_statements_status ON statements(status);
CREATE INDEX idx_statements_statement_month ON statements(statement_month DESC);
