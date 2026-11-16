create table test_status (
  test_case_id    bigint      primary key references test_cases(id) on delete cascade,
  project_id      bigint      not null references projects(id) on delete cascade,
  status          text        not null check (status in
                    ('STABLE', 'FLAKY', 'SUSPECTED', 'FAILING', 'BROKEN', 'NOT_ENOUGH_DATA')),
  status_since    timestamptz not null,
  by_variant      jsonb       not null default '{}'::jsonb,   -- {"": "STABLE", "firefox": "FLAKY"}
  evidence        jsonb       not null default '[]'::jsonb,   -- up to 5 most recent items
  impact          int         not null default 0,
  flip_rate       numeric(5,4) not null default 0,
  last_failure_at timestamptz,
  computed_at     timestamptz not null
);
create index test_status_project_status_idx on test_status (project_id, status, impact desc);

create table test_status_events (
  id           bigint generated always as identity primary key,
  project_id   bigint      not null references projects(id) on delete cascade,
  test_case_id bigint      not null references test_cases(id) on delete cascade,
  from_status  text,                       -- null for a brand-new test
  to_status    text        not null,
  reason       text        not null,       -- e.g. 'RETRY_PASS in run 9876543210'
  occurred_at  timestamptz not null
);
create index test_status_events_project_time_idx on test_status_events (project_id, occurred_at desc);

create table project_daily_snapshots (
  project_id      bigint not null references projects(id) on delete cascade,
  day             date   not null,
  stable          int    not null,
  flaky           int    not null,
  suspected       int    not null,
  failing         int    not null,
  broken          int    not null,
  not_enough_data int    not null,
  primary key (project_id, day)
);
