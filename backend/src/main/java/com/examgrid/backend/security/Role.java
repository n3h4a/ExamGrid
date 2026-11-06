package com.examgrid.backend.security;

/** Application roles. The JWT carries one role; Spring Security sees it as {@code ROLE_<name>}. */
public enum Role {
    /** Exam cell administrators: import data, run the solver, approve and publish. */
    EXAM_CELL,
    /** Faculty: see their own invigilation duties. */
    FACULTY,
    /** Students: see their own timetable, seats and hall ticket. */
    STUDENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
