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
] as const;

export async function bootstrapSchema(client: DbClient): Promise<void> {
  for (const statement of SCHEMA_STATEMENTS) {
    await client.query(statement);
  }
}
