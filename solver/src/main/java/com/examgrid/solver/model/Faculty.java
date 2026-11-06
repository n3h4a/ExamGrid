package com.examgrid.solver.model;

import java.util.Set;

/**
 * A member of staff who can invigilate.
 *
 * @param id                 unique staff id
 * @param name               display name
 * @param teaches            course codes this person teaches (they never invigilate those papers)
 * @param unavailableSlotIds slots the person is away for
 */
public record Faculty(String id, String name, Set<String> teaches, Set<String> unavailableSlotIds) {

    public Faculty {
        id = Validation.requireText(id, "faculty id");
        name = (name == null || name.isBlank()) ? id : name.strip();
        teaches = teaches == null ? Set.of() : Set.copyOf(teaches);
        unavailableSlotIds = unavailableSlotIds == null ? Set.of() : Set.copyOf(unavailableSlotIds);
    }

    public static Faculty of(String id) {
        return new Faculty(id, id, Set.of(), Set.of());
    }

    public boolean isAvailable(String slotId) {
        return !unavailableSlotIds.contains(slotId);
    }
}
