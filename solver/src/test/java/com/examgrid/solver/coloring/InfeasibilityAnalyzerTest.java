package com.examgrid.solver.coloring;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.InfeasibilityIssue.Type;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.Session;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.examgrid.solver.coloring.ColoringFixtures.analyzer;
import static com.examgrid.solver.coloring.ColoringFixtures.problem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfeasibilityAnalyzerTest {

    @Test
    void studentWithMoreExamsThanSlotsIsNamed() {
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(20, 1));
        for (int i = 0; i < 25; i++) {
            b.course(Course.of("C" + i)).enrol("21CS045", "C" + i).enrol("OTHER" + i, "C" + i);
        }
        List<InfeasibilityIssue> issues = issues(b.build());
        InfeasibilityIssue overload = only(issues, Type.STUDENT_OVERLOAD);
        assertThat(overload.students()).containsExactly("21CS045");
        assertThat(overload.message()).contains("25 exams").contains("only 20 slots");
        assertThat(overload.proof()).isTrue();
    }

    @Test
    void cliqueLargerThanSlotCountIsAProofWithWitnesses() {
        ProblemInstance instance = TestInstances.specExample(2).build();
        InfeasibilityIssue clique = only(issues(instance), Type.CLIQUE_TOO_LARGE);
        assertThat(clique.courses()).containsExactlyInAnyOrder("CS301", "MA201", "PH101");
        assertThat(clique.students()).contains("ASHA", "MEENA", "RAVI");
        assertThat(clique.message()).contains("need 3 different slots").contains("only 2");
    }

    @Test
    void sessionRulesCountTowardsHallsCondition() {
        // Two afternoon-only labs for one student, but just one afternoon slot.
        ProblemInstance instance = ProblemInstance.builder()
                .course(new Course("LAB1", "", false, Set.of(Session.AFTERNOON)))
                .course(new Course("LAB2", "", false, Set.of(Session.AFTERNOON)))
                .enrol("S", "LAB1").enrol("S", "LAB2")
                .slots(TestInstances.slots(1, 2))
                .build();
        InfeasibilityIssue overload = only(issues(instance), Type.STUDENT_OVERLOAD);
        assertThat(overload.message()).contains("leave only 1 distinct slots");
    }

    @Test
    void lockedNeighboursInTheSameSlotAreReported() {
        String slot = TestInstances.slots(3, 1).getFirst().id();
        ProblemInstance instance = TestInstances.specExample(3).lock("CS301", slot).lock("MA201", slot).build();
        InfeasibilityIssue lock = only(issues(instance), Type.LOCK_CONFLICT);
        assertThat(lock.students()).containsExactly("ASHA");
        assertThat(lock.message()).contains("both locked to " + slot);
    }

    @Test
    void sameSlotGroupWithSharedStudentIsReported() {
        ProblemInstance instance = TestInstances.specExample(3).sameSlot(Set.of("CS301", "MA201")).build();
        InfeasibilityIssue group = only(issues(instance), Type.SAME_SLOT_GROUP_CONFLICT);
        assertThat(group.students()).containsExactly("ASHA");
    }

    @Test
    void emptyDomainIsReported() {
        ProblemInstance instance = ProblemInstance.builder()
                .course(new Course("LAB1", "", false, Set.of(Session.AFTERNOON)))
                .enrol("S", "LAB1")
                .slots(TestInstances.slots(3, 1))
                .build();
        assertThat(only(issues(instance), Type.EMPTY_DOMAIN).courses()).containsExactly("LAB1");
    }

    @Test
    void paperLargerThanAllRoomsIsReported() {
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(3, 1))
                .room(Room.grid("R1", 2, 5)).course(Course.of("BIG"));
        for (int i = 0; i < 11; i++) {
            b.enrol("S" + i, "BIG");
        }
        assertThat(only(issues(b.build()), Type.PAPER_TOO_LARGE).message()).contains("seat only 10");
    }

    @Test
    void paperThatCannotBeSeatedApartIsReported() {
        // 10 seats, but at most 5 candidates of one paper can avoid sitting next to each other.
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(3, 1))
                .room(Room.grid("R1", 2, 5)).course(Course.of("BIG"));
        for (int i = 0; i < 7; i++) {
            b.enrol("S" + i, "BIG");
        }
        assertThat(only(issues(b.build()), Type.PAPER_TOO_LARGE).message()).contains("at most 5");
    }

    @Test
    void totalCapacityIsReported() {
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(2, 1))
                .room(Room.grid("R1", 2, 4));
        for (String c : new String[] {"A", "B", "C", "D", "E"}) {
            b.course(Course.of(c));
            for (int i = 0; i < 4; i++) {
                b.enrol(c + i, c);
            }
        }
        assertThat(only(issues(b.build()), Type.CAPACITY_EXCEEDED).message()).contains("20 exam seats");
    }

    @Test
    void feasibleInstanceHasNoStructuralIssues() {
        assertThat(issues(TestInstances.specExample(3).build())).isEmpty();
    }

    @Test
    void initialTimetableThrowsWithTheReport() {
        ProblemInstance instance = TestInstances.specExample(2).build();
        ColoringProblem p = problem(instance);
        assertThatThrownBy(() -> new InitialTimetable(1_000, 1).build(analyzer(instance, p), p))
                .isInstanceOfSatisfying(InfeasibleException.class, e -> {
                    assertThat(e.report().proven()).isTrue();
                    assertThat(e.getMessage()).startsWith("No clash-free timetable exists");
                });
    }

    @Test
    void initialTimetableSucceedsOnFeasibleInput() {
        ProblemInstance instance = TestInstances.specExample(3).build();
        ColoringProblem p = problem(instance);
        InitialTimetable.Result r = new InitialTimetable(1_000, 1).build(analyzer(instance, p), p);
        assertThat(r.colorsUsed()).isEqualTo(3);
        assertThat(r.repaired()).isFalse();
    }

    private static List<InfeasibilityIssue> issues(ProblemInstance instance) {
        ColoringProblem p = problem(instance);
        return analyzer(instance, p).structuralIssues();
    }

    private static InfeasibilityIssue only(List<InfeasibilityIssue> issues, Type type) {
        List<InfeasibilityIssue> matching = issues.stream().filter(i -> i.type() == type).toList();
        assertThat(matching).as("issues of type %s in %s", type, issues).hasSize(1);
        return matching.getFirst();
    }
}
