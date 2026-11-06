package com.examgrid.solver.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * A course whose exam has to be scheduled.
 *
 * @param code            unique course code, e.g. {@code CS301}
 * @param name            human readable title
 * @param hard            whether the paper counts as "hard" for the minimum-gap rule
 * @param allowedSessions sessions this exam may be held in; empty means any session
 *                        (models rules such as "labs only in the afternoon")
 */
public record Course(String code, String name, boolean hard, Set<Session> allowedSessions) {

    public Course {
        code = Validation.requireText(code, "course code");
        name = (name == null || name.isBlank()) ? code : name.strip();
        allowedSessions = allowedSessions == null || allowedSessions.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(allowedSessions));
    }

    public static Course of(String code) {
        return new Course(code, code, false, Set.of());
    }

    public static Course hard(String code) {
        return new Course(code, code, true, Set.of());
    }

    public boolean allows(Session session) {
        return allowedSessions.isEmpty() || allowedSessions.contains(session);
    }
}
