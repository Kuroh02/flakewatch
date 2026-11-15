create table ci_runs (
  id              bigint generated always as identity primary key,
  project_id      bigint      not null references projects(id) on delete cascade,
  provider        text        not null check (provider in ('GITHUB', 'JENKINS', 'GITLAB', 'OTHER')),
  external_run_id text        not null check (char_length(external_run_id) between 1 and 100),
  attempt         int         not null default 1 check (attempt >= 1),
  commit_sha      text        not null check (commit_sha ~ '^[0-9a-f]{7,64}$'),
  branch          text        not null check (char_length(branch) between 1 and 255),
  pull_request    int,
  repository_url  text,
  started_at      timestamptz not null,   -- from metadata; otherwise the first upload's received time
  created_at      timestamptz not null default now(),
  unique (project_id, provider, external_run_id, attempt)
);
create index ci_runs_project_started_idx on ci_runs (project_id, started_at desc);
create index ci_runs_project_commit_idx  on ci_runs (project_id, commit_sha);

create table uploads (
  id               bigint generated always as identity primary key,
  project_id       bigint      not null references projects(id) on delete cascade,
  run_id           bigint      not null references ci_runs(id) on delete cascade,
  job_key          text        not null default 'default' check (char_length(job_key) between 1 and 100),
  variant          text        not null default ''        check (char_length(variant) <= 100),
  content_sha256   bytea       not null,   -- hash over metadata + every file's hash, in a fixed order
  status           text        not null check (status in ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED')),
  file_count       int         not null check (file_count between 1 and 20),
  compressed_bytes bigint      not null,
  received_at      timestamptz not null,
  processed_at     timestamptz,
  error            text,                   -- why it FAILED, readable by the user
  warnings         jsonb       not null default '[]'::jsonb,
  summary          jsonb,                  -- {tests, passed, failed, errored, skipped, retried}
  unique (run_id, job_key)                 -- the idempotency key (FR-ING-004)
);
create index uploads_project_received_idx on uploads (project_id, received_at desc);

create table upload_files (
  id             bigint generated always as identity primary key,
  upload_id      bigint      not null references uploads(id) on delete cascade,
  file_name      text        not null,
  content_gzip   bytea,                    -- set to null by the 7-day purge (FR-ING-012)
  original_bytes bigint      not null,
  purged_at      timestamptz
);
create index upload_files_upload_idx on upload_files (upload_id);

create table jobs (
  id               bigint generated always as identity primary key,
  kind             text        not null,   -- PROCESS_UPLOAD, RECLASSIFY_TESTS, RECLASSIFY_AGING, PURGE_RAW_FILES, ...
  payload          jsonb       not null,
  dedupe_key       text,                   -- stops the same job being queued twice
  status           text        not null check (status in ('PENDING', 'RUNNING', 'DONE', 'DEAD')),
  attempts         int         not null default 0,
  max_attempts     int         not null default 5,
  run_after        timestamptz not null,
  lease_token      uuid,                   -- the fencing token
  lease_expires_at timestamptz,
  last_error       text,
  created_at       timestamptz not null,
  updated_at       timestamptz not null,
  finished_at      timestamptz
);
create index jobs_ready_idx  on jobs (run_after)        where status = 'PENDING';
create index jobs_lease_idx  on jobs (lease_expires_at) where status = 'RUNNING';
create unique index jobs_dedupe_uq on jobs (dedupe_key)
  where dedupe_key is not null and status in ('PENDING', 'RUNNING');
