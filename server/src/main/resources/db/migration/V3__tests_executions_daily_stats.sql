create extension if not exists pg_trgm;   -- fast "search by name" (FR-UI-002)

create table test_cases (
  id            bigint generated always as identity primary key,
  project_id    bigint      not null references projects(id) on delete cascade,
  key_hash      bytea       not null,      -- SHA-256 of test_key (keys can be very long)
  test_key      text        not null,      -- '<suite>#<name>'
  method_key    text        not null,      -- test_key without parameter suffix (quarantine level)
  suite         text        not null,
  name          text        not null,
  file          text,
  first_seen_at timestamptz not null,
  last_seen_at  timestamptz not null,
  unique (project_id, key_hash)
);
create index test_cases_project_method_idx on test_cases (project_id, method_key);
create index test_cases_key_trgm_idx       on test_cases using gin (test_key gin_trgm_ops);

create table failure_groups (
  id                 bigint generated always as identity primary key,
  project_id         bigint      not null references projects(id) on delete cascade,
  fingerprint        bytea       not null,
  failure_type       text        not null,
  normalized_message text        not null,
  sample_message     text        not null,     -- one original message, trimmed to 4 KB
  top_frames         text[]      not null default '{}',
  first_seen_at      timestamptz not null,
  last_seen_at       timestamptz not null,
  occurrences        bigint      not null default 0,   -- all-time; survives execution retention
  unique (project_id, fingerprint)
);

create table test_executions (
  id               bigint generated always as identity primary key,
  project_id       bigint      not null references projects(id) on delete cascade,
  test_case_id     bigint      not null references test_cases(id) on delete cascade,
  upload_id        bigint      not null references uploads(id) on delete cascade,
  run_id           bigint      not null references ci_runs(id) on delete cascade,
  execution_index  smallint    not null check (execution_index >= 0),
  is_final         boolean     not null,   -- last execution of this test in this upload
  status           text        not null check (status in ('PASSED', 'FAILED', 'ERRORED', 'SKIPPED')),
  quarantined      boolean     not null default false,   -- FR-ING-013
  duration_ms      int         not null default 0 check (duration_ms >= 0),
  executed_at      timestamptz not null,   -- copy of ci_runs.started_at, for fast time-range scans
  failure_group_id bigint      references failure_groups(id) on delete set null,
  failure_type     text,
  failure_message  text,                   -- trimmed to 4 KB
  stack_trace      text,                   -- trimmed to 16 KB
  unique (upload_id, test_case_id, execution_index)   -- makes reprocessing harmless (NFR-REL-002)
);
create index test_executions_case_time_idx    on test_executions (test_case_id, executed_at desc);
create index test_executions_run_idx          on test_executions (run_id);
create index test_executions_project_time_idx on test_executions (project_id, executed_at);

create table test_daily_stats (
  test_case_id      bigint  not null references test_cases(id) on delete cascade,
  project_id        bigint  not null references projects(id) on delete cascade,
  day               date    not null,
  default_branch    boolean not null,     -- whether the run was on the default branch at ingest time
  passed            int     not null default 0,
  failed            int     not null default 0,
  skipped           int     not null default 0,
  retried           int     not null default 0,
  total_duration_ms bigint  not null default 0,
  primary key (test_case_id, day, default_branch)
);
