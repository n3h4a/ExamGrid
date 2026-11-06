-- Trigram indexes power the admin search box: substring and fuzzy matches on roll numbers,
-- names and codes stay fast as the tables grow.
create extension if not exists pg_trgm;

create index student_roll_trgm on student using gin (roll gin_trgm_ops);
create index student_name_trgm on student using gin (name gin_trgm_ops);
create index course_code_trgm on course using gin (code gin_trgm_ops);
create index course_name_trgm on course using gin (name gin_trgm_ops);
create index faculty_code_trgm on faculty using gin (code gin_trgm_ops);
create index faculty_name_trgm on faculty using gin (name gin_trgm_ops);
