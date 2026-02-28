CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    institution         VARCHAR(255),
    type                VARCHAR(50) NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'LOAN')),
    last4               VARCHAR(4),
    credit_limit        DECIMAL(12,2),
    current_balance     DECIMAL(12,2),
    available_credit    DECIMAL(12,2),
    apr                 DECIMAL(5,2),
    promo_apr           DECIMAL(5,2),
    promo_apr_end_date  DATE,
    payment_due_day     INTEGER CHECK (payment_due_day BETWEEN 1 AND 31),
    min_payment         DECIMAL(12,2),
    rewards_program     VARCHAR(255),
    color               VARCHAR(7),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_accounts_type ON accounts(type);
CREATE INDEX idx_accounts_institution ON accounts(institution);
CREATE INDEX idx_accounts_is_active ON accounts(is_active);
