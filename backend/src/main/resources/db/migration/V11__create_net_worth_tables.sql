CREATE TABLE manual_assets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    asset_type    VARCHAR(20) NOT NULL CHECK (asset_type IN ('ASSET','LIABILITY')),
    asset_class   VARCHAR(50) NOT NULL CHECK (asset_class IN
                  ('REAL_ESTATE','VEHICLE','INVESTMENT','LOAN','RETIREMENT','OTHER')),
    current_value DECIMAL(14,2) NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE TABLE net_worth_snapshots (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_date      DATE NOT NULL,
    liquid_assets      DECIMAL(14,2) NOT NULL DEFAULT 0,
    credit_card_debt   DECIMAL(14,2) NOT NULL DEFAULT 0,
    manual_assets      DECIMAL(14,2) NOT NULL DEFAULT 0,
    manual_liabilities DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_worth          DECIMAL(14,2) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_net_worth_snapshots_date ON net_worth_snapshots(snapshot_date);
