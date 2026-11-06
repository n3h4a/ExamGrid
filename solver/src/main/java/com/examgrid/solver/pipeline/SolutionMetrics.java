package com.examgrid.solver.pipeline;

import com.examgrid.solver.optimize.TimetableMetrics;

/**
 * Headline numbers of a plan, shown on the review screen and used to compare runs.
 *
 * @param timetable           student-level timetable quality
 * @param dsaturColours       slots used by the initial DSatur colouring
 * @param repaired            whether TabuCol repair was needed
 * @param initialScore        score before optimisation
 * @param optimiserIterations moves attempted by the optimiser
 * @param optimiserStop       why the optimiser stopped
 * @param roomSlotsUsed       (room, slot) pairs in use
 * @param seatUtilisation     candidates / seats of the rooms in use, 0–1
 * @param invigilationDuties  invigilators assigned
 * @param unfilledDuties      invigilators missing
 * @param minFacultyDuties    lightest duty load among faculty
 * @param maxFacultyDuties    heaviest duty load among faculty
 * @param elapsedMillis       total solve time
 */
public record SolutionMetrics(TimetableMetrics timetable, int dsaturColours, boolean repaired, long initialScore,
                              long optimiserIterations, String optimiserStop, int roomSlotsUsed,
                              double seatUtilisation, int invigilationDuties, int unfilledDuties,
                              int minFacultyDuties, int maxFacultyDuties, long elapsedMillis) {

    public long score() {
        return timetable.score();
    }
}
