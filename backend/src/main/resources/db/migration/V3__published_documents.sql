-- Documents generated when a run is published: one hall ticket per student, one seating
-- chart per slot, the timetable and the invigilation roster. The bytes live in file storage.
create table published_document (
    id           bigint generated always as identity primary key,
    run_id       bigint       not null references schedule_run (id) on delete cascade,
    kind         varchar(32)  not null check (kind in ('HALL_TICKET', 'SEATING', 'TIMETABLE', 'INVIGILATION')),
    ref          varchar(64)  not null,
    storage_key  varchar(512) not null,
    size_bytes   bigint       not null,
    created_at   timestamptz  not null default now(),
    constraint published_document_uq unique (run_id, kind, ref)
);

create index seat_assignment_student_idx on seat_assignment (run_id, student_id);
