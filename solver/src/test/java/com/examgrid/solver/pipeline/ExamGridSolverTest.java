package com.examgrid.solver.pipeline;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.coloring.InfeasibilityIssue;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.InvalidInstanceException;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.CancellationToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamGridSolverTest {

    private static final ProblemInstance UNIVERSITY =
            SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(120, 1500));

    @Test
    void producesAValidCompletePlanForASyntheticUniversity() {
        List<ProgressEvent> events = new ArrayList<>();
        Solution solution = new ExamGridSolver().solve(UNIVERSITY, events::add, CancellationToken.none());

        assertThat(SolutionValidator.validate(UNIVERSITY, solution)).isEmpty();
        assertThat(solution.seats()).hasSize(UNIVERSITY.enrolmentCount());
        assertThat(solution.exams()).hasSize(UNIVERSITY.courseCount());
        assertThat(solution.metrics().timetable().clashPairs()).isZero();
        assertThat(solution.metrics().unfilledDuties()).isZero();
        assertThat(solution.metrics().maxFacultyDuties() - solution.metrics().minFacultyDuties()).isLessThanOrEqualTo(2);
        assertThat(solution.metrics().seatUtilisation()).isBetween(0.5, 1.0);
        assertThat(events.getLast().phase()).isEqualTo(Phase.DONE);
        assertThat(events).extracting(ProgressEvent::percent).isSorted();
    }

    @Test
    void seatsWithinAPaperAndRoomFollowRollOrder() {
        Solution solution = new ExamGridSolver().solve(UNIVERSITY);
        Map<String, List<SeatAssignment>> groups = solution.seats().stream()
                .collect(Collectors.groupingBy(s -> s.slotId() + s.roomCode() + s.courseCode()));
        for (List<SeatAssignment> group : groups.values()) {
            List<SeatAssignment> bySeat = group.stream()
                    .sorted(Comparator.comparing(SeatAssignment::seat)).toList();
            assertThat(bySeat).extracting(SeatAssignment::roll).isSorted();
        }
    }

    @Test
    void requiresRooms() {
        assertThatThrownBy(() -> new ExamGridSolver().solve(TestInstances.specExample(3).build()))
                .isInstanceOf(InvalidInstanceException.class);
    }

    @Test
    void reportsSlotsThatCannotBeSeated() {
        // One slot, a 2 × 3 room with diagonal adjacency and three 2-candidate papers. Every
        // structural check passes (6 seats, at most 2 of a paper apart), but the middle column
        // touches every other seat, so no seating exists.
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(1, 1))
                .room(Room.grid("R", 2, 3))
                .params(SolverParams.builder().diagonalAdjacency(true).build());
        for (String c : new String[] {"A", "B", "C"}) {
            b.course(Course.of(c)).enrol(c + "1", c).enrol(c + "2", c);
        }
        assertThatThrownBy(() -> new ExamGridSolver().solve(b.build()))
                .isInstanceOfSatisfying(InfeasibleException.class, e -> assertThat(e.report().issues())
                        .extracting(InfeasibilityIssue::type).containsExactly(InfeasibilityIssue.Type.SEATING));
    }

    @Test
    void diagonalAdjacencyIsHonoured() {
        ProblemInstance diagonal = UNIVERSITY.withParams(
                SolverParams.builder().diagonalAdjacency(true).maxIterations(50_000).build());
        // Diagonal rules quarter the same-paper capacity; give the instance more rooms.
        ProblemInstance.Builder b = diagonal.toBuilder();
        for (int i = 0; i < diagonal.roomCount(); i++) {
            b.room(Room.grid("X" + i, 8, 10));
        }
        ProblemInstance instance = b.build();
        Solution solution = new ExamGridSolver().solve(instance);
        assertThat(SolutionValidator.validate(instance, solution)).isEmpty();
    }
}
