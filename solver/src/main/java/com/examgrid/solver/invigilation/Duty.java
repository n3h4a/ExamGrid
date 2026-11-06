package com.examgrid.solver.invigilation;

import java.util.Set;

/**
 * One room in one slot that needs supervising.
 *
 * @param slot       slot index
 * @param room       room index
 * @param courses    course indexes sat in the room (their teachers are excluded)
 * @param required   number of invigilators needed
 */
public record Duty(int slot, int room, Set<Integer> courses, int required) {

    public Duty {
        courses = Set.copyOf(courses);
        if (required < 1) {
            throw new IllegalArgumentException("a duty needs at least one invigilator");
        }
    }
}
