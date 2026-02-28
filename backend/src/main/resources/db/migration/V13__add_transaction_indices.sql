CREATE INDEX IF NOT EXISTS idx_transactions_type_date     ON transactions(type, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_category_date ON transactions(category, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant_date ON transactions(merchant_name, transaction_date DESC);
