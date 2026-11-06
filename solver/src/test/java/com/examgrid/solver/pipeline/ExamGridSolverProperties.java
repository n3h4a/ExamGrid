package com.examgrid.solver.pipeline;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SolverParams;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.api.statistics.StatisticsReport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarantees from the specification, checked on random universities: no student has two
 * exams in one slot, no room is over capacity, no two adjacent seats hold the same paper and
 * every enrolled student has exactly one seat per exam.
 */
class ExamGridSolverProperties {

    @Property(tries = 60)
    @StatisticsReport(onFailureOnly = false)
    void everyPlanSatisfiesTheHardConstraints(@ForAll long seed) {
        ProblemInstance instance = randomUniversity(seed);
        Solution solution;
        try {
            solution = new ExamGridSolver().solve(instance);
        } catch (InfeasibleException e) {
            Statistics.collect("infeasible");
            assertThat(e.report().issues()).isNotEmpty();
            return;
        }
        Statistics.collect("solved");
        assertThat(SolutionValidator.validate(instance, solution)).isEmpty();

        // Independent re-check of the four headline guarantees.
        Map<String, String> slotOf = new HashMap<>();
        solution.exams().forEach(e -> slotOf.put(e.courseCode(), e.slotId()));
        for (int s = 0; s < instance.studentCount(); s++) {
            Set<String> slots = new HashSet<>();
            for (int c : instance.coursesOf(s)) {
                assertThat(slots.add(slotOf.get(instance.course(c).code()))).isTrue();
            }
        }
        Map<String, Integer> perRoom = new HashMap<>();
        Map<String, Integer> perEnrolment = new HashMap<>();
        for (SeatAssignment a : solution.seats()) {
            perRoom.merge(a.slotId() + a.roomCode(), 1, Integer::sum);
            perEnrolment.merge(a.roll() + "|" + a.courseCode(), 1, Integer::sum);
        }
        perRoom.forEach((k, n) -> assertThat(n).isLessThanOrEqualTo(48));
        assertThat(perEnrolment).hasSize(instance.enrolmentCount());
        assertThat(perEnrolment.values()).containsOnly(1);
    }

    @Property(tries = 60)
    void mostRandomUniversitiesAreSolvable(@ForAll long seed) {
        boolean solved;
        try {
            new ExamGridSolver().solve(randomUniversity(seed));
            solved = true;
        } catch (InfeasibleException e) {
            solved = false;
        }
        Statistics.collect(solved);
        Statistics.coverage(c -> c.check(true).percentage(p -> p >= 60));
    }

    private static ProblemInstance randomUniversity(long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        int courses = 5 + rnd.nextInt(25);
        int students = 20 + rnd.nextInt(150);
        ProblemInstance.Builder b = ProblemInstance.builder()
                .slots(TestInstances.slots(4 + rnd.nextInt(5), 2))
                .params(SolverParams.builder().maxIterations(10_000).seed(seed).build());
        for (int i = 0; i < courses; i++) {
            b.course(new Course("C" + i, "", rnd.nextInt(4) == 0, Set.of()));
        }
        for (int s = 0; s < students; s++) {
            int load = 1 + rnd.nextInt(4);
            for (int j = 0; j < load; j++) {
                b.enrol("S" + String.format("%03d", s), "C" + rnd.nextInt(courses));
            }
        }
        int rooms = 2 + rnd.nextInt(5);
        for (int r = 0; r < rooms; r++) {
            b.room(Room.grid("R" + r, 3 + rnd.nextInt(4), 4 + rnd.nextInt(5)));
        }
        for (int f = 0; f < rooms * 3; f++) {
            b.faculty(new Faculty("F" + f, "", Set.of("C" + rnd.nextInt(courses)), Set.of()));
        }
        return b.build();
    }
}
