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
