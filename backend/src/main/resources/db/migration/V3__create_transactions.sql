CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID REFERENCES accounts(id) ON DELETE SET NULL,
    statement_id     UUID REFERENCES statements(id) ON DELETE SET NULL,
    transaction_date DATE NOT NULL,
    post_date        DATE,
    description      VARCHAR(500),
    merchant_name    VARCHAR(255),
    category         VARCHAR(100),
    subcategory      VARCHAR(100),
    amount           DECIMAL(12,2) NOT NULL,
    type             VARCHAR(50) CHECK (type IN ('DEBIT', 'CREDIT', 'PAYMENT', 'FEE', 'INTEREST')),
    reference_number VARCHAR(100),
    is_recurring     BOOLEAN DEFAULT FALSE,
    is_flagged       BOOLEAN DEFAULT FALSE,
    flag_reason      TEXT,
    notes            TEXT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_statement_id ON transactions(statement_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date DESC);
CREATE INDEX idx_transactions_category ON transactions(category);
CREATE INDEX idx_transactions_merchant ON transactions(merchant_name);
CREATE INDEX idx_transactions_is_flagged ON transactions(is_flagged) WHERE is_flagged = TRUE;
CREATE INDEX idx_transactions_is_recurring ON transactions(is_recurring) WHERE is_recurring = TRUE;
