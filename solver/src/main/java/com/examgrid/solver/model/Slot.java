package com.examgrid.solver.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

/**
 * A single exam sitting, for example "2026-12-01 MORNING". Slots are the colours of the
 * conflict graph.
 */
public record Slot(String id, LocalDate date, Session session) implements Comparable<Slot> {

    private static final Comparator<Slot> CHRONOLOGICAL =
            Comparator.comparing(Slot::date).thenComparing(Slot::session).thenComparing(Slot::id);

    public Slot {
        id = Validation.requireText(id, "slot id");
        Objects.requireNonNull(date, "slot date");
        Objects.requireNonNull(session, "slot session");
    }

    public static Slot of(LocalDate date, Session session) {
        return new Slot(date + "-" + session.name(), date, session);
    }

    @Override
    public int compareTo(Slot other) {
        return CHRONOLOGICAL.compare(this, other);
    }
}
