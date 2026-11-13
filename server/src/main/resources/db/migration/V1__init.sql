create table users (
  id            bigint generated always as identity primary key,
  github_id     bigint      not null unique,
  login         text        not null,
  display_name  text,
  avatar_url    text,
  created_at    timestamptz not null default now(),
  last_login_at timestamptz
);

create table projects (
  id             bigint generated always as identity primary key,
  slug           text        not null unique
                 check (slug ~ '^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$'),   -- 3–40 chars
  name           text        not null check (char_length(name) between 1 and 80),
  owner_id       bigint      references users(id) on delete set null,  -- null for bootstrap projects (FR-PRJ-007)
  visibility     text        not null default 'PRIVATE'
                 check (visibility in ('PUBLIC', 'PRIVATE')),
  default_branch text        not null default 'main',
  settings       jsonb       not null default '{}'::jsonb,              -- detection settings overrides
  created_at     timestamptz not null default now()
);

create table api_tokens (
  id           bigint generated always as identity primary key,
  project_id   bigint      not null references projects(id) on delete cascade,
  name         text        not null check (char_length(name) between 1 and 60),
  scope        text        not null check (scope in ('INGEST', 'READ')),
  prefix       text        not null,          -- e.g. 'fw_ing_7Hq2' — safe to display
  token_hash   bytea       not null unique,   -- SHA-256 of the full token
  created_by   bigint      references users(id) on delete set null,
  created_at   timestamptz not null default now(),
  last_used_at timestamptz,                   -- updated at most every 5 minutes per token
  revoked_at   timestamptz
);
create index api_tokens_project_idx on api_tokens (project_id);
