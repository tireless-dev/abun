import type { DbClient } from "./transaction";

const SCHEMA_STATEMENTS = [
  `
    create table if not exists sync_server_version (
      id integer primary key,
      next_value bigint not null
    )
  `,
  `
    insert into sync_server_version (id, next_value)
    values (1, 0)
    on conflict (id) do nothing
  `,
  `
    create table if not exists user_account (
      id text primary key,
      email text not null unique,
      created_at text not null
    )
  `,
  `
    create table if not exists preference (
      user_id text not null,
      pref_key text not null,
      pref_value text,
      value_type text not null,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null,
      primary key (user_id, pref_key)
    )
  `,
  `
    create table if not exists routine (
      id text primary key,
      user_id text not null,
      template_title text not null,
      cron_schedule text,
      template_detail text,
      recurrence_rule text,
      default_start_not_before text,
      default_estimated_duration text,
      is_active boolean not null default true,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    alter table routine add column if not exists recurrence_rule text
  `,
  `
    alter table routine add column if not exists default_start_not_before text
  `,
  `
    alter table routine add column if not exists default_estimated_duration text
  `,
  `
    create table if not exists task (
      id text primary key,
      user_id text not null,
      parent_id text,
      routine_id text,
      title text not null,
      detail text,
      start_not_before text,
      end_not_after text,
      estimated_duration text,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    alter table task add column if not exists parent_id text
  `,
  `
    alter table task add column if not exists routine_id text
  `,
  `
    alter table task add column if not exists detail text
  `,
  `
    alter table task add column if not exists start_not_before text
  `,
  `
    alter table task add column if not exists end_not_after text
  `,
  `
    alter table task add column if not exists estimated_duration text
  `,
  `
    create table if not exists alarm (
      id text primary key,
      user_id text not null,
      task_id text not null,
      trigger_time text not null,
      is_active boolean not null default true,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists task_event (
      id text primary key,
      user_id text not null,
      task_id text not null,
      journal_date text not null,
      event_type text not null,
      content text,
      postponed_json text,
      event_time text not null,
      is_deleted boolean not null default false,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    alter table task_event add column if not exists postponed_json text
  `,
  `
    alter table task_event add column if not exists server_updated_at text
  `,
  `
    create table if not exists pomodoro_session (
      id text primary key,
      user_id text not null,
      task_id text,
      phase text not null,
      state text not null,
      started_at text not null,
      ends_at text not null,
      completed_at text,
      duration_minutes integer not null,
      note text,
      task_update text not null,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists otp_code (
      email text primary key,
      code text not null,
      expires_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists user_session (
      id text primary key,
      user_id text not null,
      refresh_token_hash text not null,
      refresh_expires_at text not null,
      revoked_at text,
      rotated_at text,
      created_at text not null,
      updated_at text not null
    )
  `,
  `
    create table if not exists finance_account (
      id text primary key,
      user_id text not null,
      path text not null,
      name text not null,
      category text not null,
      currency text not null default 'CNY',
      include_in_net_worth boolean not null default true,
      is_pass_through boolean not null default false,
      pass_through_expense_account_id text,
      liquid_tier text not null default 'None',
      statement_day integer,
      due_day integer,
      is_archived boolean not null default false,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_exchange_rate (
      id text primary key,
      user_id text not null,
      base_currency text not null,
      quote_currency text not null,
      rate_date text not null,
      rate_micros bigint not null,
      source text not null default 'Manual',
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_transaction (
      id text primary key,
      user_id text not null,
      tx_date text not null,
      tx_time text,
      payee text,
      summary text not null,
      status text not null default 'Cleared',
      source_plan_id text,
      period_index integer,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_split (
      id text primary key,
      user_id text not null,
      transaction_id text not null,
      account_id text not null,
      amount_minor bigint not null,
      currency text not null default 'CNY',
      weight_amount_minor bigint not null,
      weight_currency text not null default 'CNY',
      narration text not null default '',
      sort_order integer not null default 0,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_clearing_group (
      id text primary key,
      user_id text not null,
      name text not null,
      group_type text not null,
      anchor_currency text not null default 'CNY',
      target_account_id text,
      statement_cycle text,
      due_date text,
      status text not null default 'Active',
      settled_at text,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_split_clearing_group (
      id text primary key,
      user_id text not null,
      split_id text not null,
      clearing_group_id text not null,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_amortization_plan (
      id text primary key,
      user_id text not null,
      name text not null,
      principal_minor bigint not null,
      currency text not null default 'CNY',
      total_periods integer not null,
      repayment_method text not null,
      annual_rate_bps integer not null default 0,
      fee_per_period_minor bigint not null default 0,
      start_date text not null,
      repayment_day integer not null,
      liability_account_id text not null,
      funding_account_id text,
      interest_account_id text not null,
      lifecycle_clearing_group_id text not null,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
  `
    create table if not exists finance_budget_rule (
      id text primary key,
      user_id text not null,
      name text not null,
      scope_type text not null,
      account_prefix text not null default 'Expenses:',
      excluded_prefixes_json text not null default '[]',
      monthly_limit_minor bigint not null,
      currency text not null default 'CNY',
      include_in_runway boolean not null default true,
      is_deleted boolean not null default false,
      hlc_map text not null,
      server_version bigint not null,
      server_updated_at text not null,
      created_at text not null
    )
  `,
] as const;

export async function bootstrapSchema(client: DbClient): Promise<void> {
  for (const statement of SCHEMA_STATEMENTS) {
    await client.query(statement);
  }
}
