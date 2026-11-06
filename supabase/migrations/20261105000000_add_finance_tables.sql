-- Supabase Migration: 8 Double-Entry Finance Tables for Abun
-- Enables multi-device local-first synchronization with HLC conflict resolution

CREATE TABLE IF NOT EXISTS finance_account (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    path TEXT NOT NULL,
    name TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    include_in_net_worth BOOLEAN NOT NULL DEFAULT TRUE,
    is_pass_through BOOLEAN NOT NULL DEFAULT FALSE,
    pass_through_expense_account_id VARCHAR(255),
    liquid_tier VARCHAR(32) NOT NULL DEFAULT 'None',
    statement_day INTEGER,
    due_day INTEGER,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_account_user_version ON finance_account(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_finance_account_user_path ON finance_account(user_id, path);

CREATE TABLE IF NOT EXISTS finance_exchange_rate (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    base_currency VARCHAR(16) NOT NULL,
    quote_currency VARCHAR(16) NOT NULL,
    rate_date VARCHAR(32) NOT NULL,
    rate_micros BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'Manual',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_exchange_rate_user_version ON finance_exchange_rate(user_id, server_version);

CREATE TABLE IF NOT EXISTS finance_transaction (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    tx_date VARCHAR(32) NOT NULL,
    tx_time VARCHAR(32),
    payee TEXT,
    summary TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Cleared',
    source_plan_id VARCHAR(255),
    period_index INTEGER,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_tx_user_version ON finance_transaction(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_finance_tx_user_date ON finance_transaction(user_id, tx_date);

CREATE TABLE IF NOT EXISTS finance_split (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    weight_amount_minor BIGINT NOT NULL,
    weight_currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    narration TEXT NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_split_user_version ON finance_split(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_finance_split_user_tx ON finance_split(user_id, transaction_id);
CREATE INDEX IF NOT EXISTS idx_finance_split_user_account ON finance_split(user_id, account_id);

CREATE TABLE IF NOT EXISTS finance_clearing_group (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name TEXT NOT NULL,
    group_type VARCHAR(32) NOT NULL,
    anchor_currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    target_account_id VARCHAR(255),
    statement_cycle VARCHAR(32),
    due_date VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'Active',
    settled_at VARCHAR(64),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_cg_user_version ON finance_clearing_group(user_id, server_version);

CREATE TABLE IF NOT EXISTS finance_split_clearing_group (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    split_id VARCHAR(255) NOT NULL,
    clearing_group_id VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_scg_user_version ON finance_split_clearing_group(user_id, server_version);
CREATE INDEX IF NOT EXISTS idx_finance_scg_user_split ON finance_split_clearing_group(user_id, split_id);
CREATE INDEX IF NOT EXISTS idx_finance_scg_user_group ON finance_split_clearing_group(user_id, clearing_group_id);

CREATE TABLE IF NOT EXISTS finance_amortization_plan (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name TEXT NOT NULL,
    principal_minor BIGINT NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    total_periods INTEGER NOT NULL,
    repayment_method VARCHAR(32) NOT NULL,
    annual_rate_bps INTEGER NOT NULL DEFAULT 0,
    fee_per_period_minor BIGINT NOT NULL DEFAULT 0,
    start_date VARCHAR(32) NOT NULL,
    repayment_day INTEGER NOT NULL,
    liability_account_id VARCHAR(255) NOT NULL,
    funding_account_id VARCHAR(255),
    interest_account_id VARCHAR(255) NOT NULL,
    lifecycle_clearing_group_id VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_plan_user_version ON finance_amortization_plan(user_id, server_version);

CREATE TABLE IF NOT EXISTS finance_budget_rule (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    name TEXT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    account_prefix TEXT NOT NULL DEFAULT 'Expenses:',
    excluded_prefixes_json TEXT NOT NULL DEFAULT '[]',
    monthly_limit_minor BIGINT NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    include_in_runway BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    hlc_map TEXT NOT NULL,
    server_version BIGINT NOT NULL,
    server_updated_at VARCHAR(255) NOT NULL,
    created_at VARCHAR(255) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_finance_budget_user_version ON finance_budget_rule(user_id, server_version);
