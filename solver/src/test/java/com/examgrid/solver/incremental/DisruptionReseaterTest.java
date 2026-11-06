package com.examgrid.solver.incremental;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.pipeline.ExamGridSolver;
import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionValidator;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisruptionReseaterTest {

    private static final ProblemInstance UNIVERSITY =
            SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(120, 1500));
    private static final Solution PLAN = new ExamGridSolver().solve(UNIVERSITY);

    @Test
    void movesOnlyTheStudentsOfTheLostRoomInThatSlot() {
        SeatAssignment busiest = busiestRoomSlot();
        String room = busiest.roomCode();
        String slot = busiest.slotId();
        long displaced = PLAN.seats().stream().filter(a -> a.roomCode().equals(room) && a.slotId().equals(slot)).count();

        DisruptionReseater.Result result = new DisruptionReseater(UNIVERSITY).reseat(PLAN, room, slot);

        assertThat(result.studentsMoved()).isEqualTo(displaced);
        assertThat(result.summary()).startsWith(displaced + " students moved, 0 others affected");
        assertThat(SolutionValidator.validate(UNIVERSITY, result.solution())).isEmpty();
        assertThat(result.solution().seats())
                .noneMatch(a -> a.roomCode().equals(room) && a.slotId().equals(slot));
        assertThat(result.solution().invigilations())
                .noneMatch(a -> a.roomCode().equals(room) && a.slotId().equals(slot));
        // Everybody else keeps exactly the same seat.
        Set<SeatAssignment> after = new HashSet<>(result.solution().seats());
        PLAN.seats().stream()
                .filter(a -> !(a.roomCode().equals(room) && a.slotId().equals(slot)))
                .forEach(a -> assertThat(after).contains(a));
        assertThat(result.solution().exams()).isEqualTo(PLAN.exams());
    }

    @Test
    void canRemoveARoomFromEverySlot() {
        String room = busiestRoomSlot().roomCode();
        DisruptionReseater.Result result = new DisruptionReseater(UNIVERSITY).reseat(PLAN, room, null);
        assertThat(result.solution().seats()).noneMatch(a -> a.roomCode().equals(room));
        assertThat(SolutionValidator.validate(UNIVERSITY, result.solution())).isEmpty();
        assertThat(result.affectedSlots()).isNotEmpty();
    }

    @Test
    void opensAReserveRoomWhenSpareSeatsRunOut() {
        // Two papers fill room A completely (checkerboard); B is an unused reserve room.
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(1, 1))
                .room(Room.grid("A", 4, 4)).room(Room.grid("B", 4, 4))
                .course(Course.of("X")).course(Course.of("Y"));
        for (int i = 0; i < 8; i++) {
            b.enrol("X" + i, "X").enrol("Y" + i, "Y");
        }
        ProblemInstance instance = b.build();
        Solution plan = new ExamGridSolver().solve(instance);
        assertThat(plan.seats()).allMatch(a -> a.roomCode().equals("A"));

        DisruptionReseater.Result result = new DisruptionReseater(instance).reseat(plan, "A", null);
        assertThat(result.studentsMoved()).isEqualTo(16);
        assertThat(result.reserveRoomsOpened()).singleElement().asString().startsWith("B");
        assertThat(SolutionValidator.validate(instance, result.solution())).isEmpty();
    }

    @Test
    void explainsWhenNothingCanAbsorbTheStudents() {
        ProblemInstance instance = ProblemInstance.builder().slots(TestInstances.slots(1, 1))
                .room(Room.grid("A", 2, 2)).course(Course.of("X")).enrol("S1", "X").build();
        Solution plan = new ExamGridSolver().solve(instance);
        assertThatThrownBy(() -> new DisruptionReseater(instance).reseat(plan, "A", null))
                .isInstanceOf(InfeasibleException.class)
                .hasMessageContaining("Without A");
    }

    private static SeatAssignment busiestRoomSlot() {
        Map<String, Long> counts = PLAN.seats().stream()
                .collect(Collectors.groupingBy(a -> a.slotId() + "|" + a.roomCode(), Collectors.counting()));
        String key = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return PLAN.seats().stream().filter(a -> (a.slotId() + "|" + a.roomCode()).equals(key))
                .min(Comparator.comparing(SeatAssignment::roll)).orElseThrow();
    }
}
