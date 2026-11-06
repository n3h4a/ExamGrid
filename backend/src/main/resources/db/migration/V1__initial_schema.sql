-- ExamGrid schema. Master data (students, courses, rooms, faculty, slots) is mutable;
-- everything hanging off schedule_run is an immutable snapshot of one solve, so master data
-- referenced by a run cannot be deleted (ON DELETE RESTRICT).

-- ---------------------------------------------------------------- identity & audit

create table app_user (
    id             bigint generated always as identity primary key,
    username       varchar(64)  not null unique,
    password_hash  varchar(100) not null,
    display_name   varchar(120) not null,
    role           varchar(16)  not null check (role in ('EXAM_CELL', 'FACULTY', 'STUDENT')),
    student_roll   varchar(32),
    faculty_code   varchar(32),
    enabled        boolean      not null default true,
    created_at     timestamptz  not null default now(),
    last_login_at  timestamptz,
    constraint app_user_student_has_roll check (role <> 'STUDENT' or student_roll is not null),
    constraint app_user_faculty_has_code check (role <> 'FACULTY' or faculty_code is not null)
);

create unique index app_user_student_roll_uq on app_user (student_roll) where student_roll is not null;
create unique index app_user_faculty_code_uq on app_user (faculty_code) where faculty_code is not null;

create table audit_log (
    id           bigint generated always as identity primary key,
    occurred_at  timestamptz not null default now(),
    actor        varchar(64) not null,
    action       varchar(64) not null,
    entity_type  varchar(64),
    entity_id    varchar(64),
    details      jsonb       not null default '{}'::jsonb,
    request_id   varchar(64)
);

create index audit_log_occurred_at_idx on audit_log (occurred_at desc);
create index audit_log_entity_idx on audit_log (entity_type, entity_id);

-- ---------------------------------------------------------------- master data

create table course (
    id                bigint generated always as identity primary key,
    code              varchar(32)  not null unique,
    name              varchar(200) not null,
    department        varchar(64)  not null default '',
    hard              boolean      not null default false,
    allowed_sessions  varchar(64)  not null default '',
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);

create table student (
    id          bigint generated always as identity primary key,
    roll        varchar(32)  not null unique,
    name        varchar(120) not null default '',
    department  varchar(64)  not null default '',
    email       varchar(254),
    created_at  timestamptz  not null default now()
);

create table import_batch (
    id                 bigint generated always as identity primary key,
    kind               varchar(32)  not null check (kind in ('ENROLMENTS', 'COURSES', 'ROOMS', 'FACULTY', 'STUDENTS')),
    original_filename  varchar(255) not null,
    content_type       varchar(128),
    size_bytes         bigint       not null,
    sha256             char(64)     not null,
    storage_key        varchar(512),
    dry_run            boolean      not null,
    status             varchar(16)  not null check (status in ('VALIDATED', 'REJECTED', 'COMMITTED')),
    total_rows         integer      not null default 0,
    accepted_rows      integer      not null default 0,
    error_count        integer      not null default 0,
    warning_count      integer      not null default 0,
    report             jsonb        not null default '{}'::jsonb,
    created_by         varchar(64)  not null,
    created_at         timestamptz  not null default now()
);

create table enrolment (
    id               bigint generated always as identity primary key,
    student_id       bigint not null references student (id) on delete cascade,
    course_id        bigint not null references course (id) on delete cascade,
    import_batch_id  bigint references import_batch (id) on delete set null,
    constraint enrolment_uq unique (student_id, course_id)
);

create index enrolment_course_idx on enrolment (course_id);

create table exam_slot (
    id         bigint generated always as identity primary key,
    slot_date  date        not null,
    session    varchar(16) not null check (session in ('MORNING', 'AFTERNOON', 'EVENING')),
    constraint exam_slot_uq unique (slot_date, session)
);

create table room (
    id            bigint generated always as identity primary key,
    code          varchar(32)  not null unique,
    building      varchar(120) not null default '',
    seat_rows     integer      not null check (seat_rows between 1 and 100),
    seat_columns  integer      not null check (seat_columns between 1 and 100),
    blocked_seats jsonb        not null default '[]'::jsonb,
    capacity      integer      not null check (capacity > 0),
    priority      integer      not null default 0,
    active        boolean      not null default true
);

create table faculty (
    id          bigint generated always as identity primary key,
    code        varchar(32)  not null unique,
    name        varchar(120) not null,
    email       varchar(254),
    department  varchar(64)  not null default ''
);

create table faculty_course (
    faculty_id  bigint not null references faculty (id) on delete cascade,
    course_id   bigint not null references course (id) on delete cascade,
    primary key (faculty_id, course_id)
);

create table faculty_unavailability (
    faculty_id  bigint not null references faculty (id) on delete cascade,
    slot_id     bigint not null references exam_slot (id) on delete cascade,
    primary key (faculty_id, slot_id)
);

create table course_lock (
    course_id  bigint primary key references course (id) on delete cascade,
    slot_id    bigint      not null references exam_slot (id) on delete cascade,
    locked_by  varchar(64) not null,
    locked_at  timestamptz not null default now()
);

create table same_slot_group (
    id    bigint generated always as identity primary key,
    name  varchar(120) not null unique
);

create table same_slot_group_member (
    group_id   bigint not null references same_slot_group (id) on delete cascade,
    course_id  bigint not null unique references course (id) on delete cascade,
    primary key (group_id, course_id)
);

-- ---------------------------------------------------------------- scheduling runs

create table schedule_run (
    id              bigint generated always as identity primary key,
    label           varchar(120),
    status          varchar(16)  not null check (status in
                        ('QUEUED', 'RUNNING', 'DONE', 'INFEASIBLE', 'FAILED', 'CANCELLED', 'PUBLISHING', 'PUBLISHED', 'SUPERSEDED')),
    params          jsonb        not null default '{}'::jsonb,
    metrics         jsonb,
    score           bigint,
    infeasibility   jsonb,
    error_message   text,
    progress_pct    integer      not null default 0,
    parent_run_id   bigint references schedule_run (id) on delete restrict,
    disruption      jsonb,
    input_summary   jsonb,
    created_by      varchar(64)  not null,
    created_at      timestamptz  not null default now(),
    started_at      timestamptz,
    finished_at     timestamptz,
    published_at    timestamptz
);

create index schedule_run_status_idx on schedule_run (status);
-- At most one plan is live at any time.
create unique index schedule_run_one_published on schedule_run ((true)) where status = 'PUBLISHED';

create table exam_assignment (
    run_id     bigint not null references schedule_run (id) on delete cascade,
    course_id  bigint not null references course (id) on delete restrict,
    slot_id    bigint not null references exam_slot (id) on delete restrict,
    primary key (run_id, course_id)
);

create table seat_assignment (
    id          bigint generated always as identity primary key,
    run_id      bigint     not null references schedule_run (id) on delete cascade,
    student_id  bigint     not null references student (id) on delete restrict,
    course_id   bigint     not null references course (id) on delete restrict,
    slot_id     bigint     not null references exam_slot (id) on delete restrict,
    room_id     bigint     not null references room (id) on delete restrict,
    seat_row    integer    not null,
    seat_col    integer    not null,
    seat_label  varchar(8) not null,
    constraint seat_assignment_seat_uq unique (run_id, slot_id, room_id, seat_row, seat_col),
    constraint seat_assignment_enrolment_uq unique (run_id, student_id, course_id)
);

create index seat_assignment_room_slot_idx on seat_assignment (run_id, slot_id, room_id);

create table invigilation (
    id          bigint generated always as identity primary key,
    run_id      bigint not null references schedule_run (id) on delete cascade,
    faculty_id  bigint not null references faculty (id) on delete restrict,
    room_id     bigint not null references room (id) on delete restrict,
    slot_id     bigint not null references exam_slot (id) on delete restrict,
    constraint invigilation_uq unique (run_id, faculty_id, slot_id)
);

create table approval (
    run_id       bigint      not null references schedule_run (id) on delete cascade,
    user_id      bigint      not null references app_user (id) on delete restrict,
    approved_at  timestamptz not null default now(),
    comment      varchar(500),
    primary key (run_id, user_id)
);
