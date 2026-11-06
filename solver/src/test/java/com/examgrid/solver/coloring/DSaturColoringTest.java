package com.examgrid.solver.coloring;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.examgrid.solver.coloring.ColoringFixtures.assertProperColouring;
import static com.examgrid.solver.coloring.ColoringFixtures.problem;
import static org.assertj.core.api.Assertions.assertThat;

class DSaturColoringTest {

    private final DSaturColoring dsatur = new DSaturColoring();

    @Test
    void specTriangleNeedsThreeSlotsAndTheIsolatedCourseReusesOne() {
        ProblemInstance instance = TestInstances.specExample(3).build();
        ColoringProblem p = problem(instance);
        ColoringResult r = dsatur.color(p);
        assertThat(r.complete()).isTrue();
        assertThat(r.colorsUsed()).isEqualTo(3);
        assertProperColouring(p, r.slotOf());
    }

    @Test
    void specTriangleWithTwoSlotsCannotBeColoured() {
        ColoringResult r = dsatur.color(problem(TestInstances.specExample(2).build()));
        assertThat(r.complete()).isFalse();
        assertThat(r.uncolored()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 6, 10, 50})
    void evenCyclesAreTwoColourable(int n) {
        assertExactColours(TestInstances.fromEdges(n, TestInstances.cycle(n), n).build(), 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 11, 49})
    void oddCyclesNeedThreeColours(int n) {
        assertExactColours(TestInstances.fromEdges(n, TestInstances.cycle(n), n).build(), 3);
    }

    @Test
    void completeBipartiteGraphIsTwoColourable() {
        assertExactColours(TestInstances.fromEdges(9, TestInstances.completeBipartite(4, 5), 9).build(), 2);
    }

    @Test
    void wheelsWithEvenRimNeedThreeAndOddRimNeedFour() {
        assertExactColours(TestInstances.fromEdges(7, TestInstances.wheel(6), 7).build(), 3);
        assertExactColours(TestInstances.fromEdges(8, TestInstances.wheel(7), 8).build(), 4);
    }

    @Test
    void completeGraphNeedsNColours() {
        assertExactColours(TestInstances.fromEdges(7, TestInstances.complete(7), 7).build(), 7);
    }

    @Test
    void lockedCoursesStayInTheirSlot() {
        ProblemInstance instance = TestInstances.specExample(3)
                .lock("CS301", TestInstances.slots(3, 1).get(2).id())
                .build();
        ColoringProblem p = problem(instance);
        ColoringResult r = dsatur.color(p);
        assertThat(r.complete()).isTrue();
        assertThat(r.slotOf()[instance.courseIndex("CS301")]).isEqualTo(2);
        assertProperColouring(p, r.slotOf());
    }

    @Test
    void sessionRulesRestrictTheDomain() {
        ProblemInstance instance = ProblemInstance.builder()
                .course(new Course("LAB1", "Lab", false, Set.of(Session.AFTERNOON)))
                .course(Course.of("TH1"))
                .enrol("S", "LAB1").enrol("S", "TH1")
                .slots(TestInstances.slots(2, 2))
                .build();
        ColoringProblem p = problem(instance);
        ColoringResult r = dsatur.color(p);
        assertThat(r.complete()).isTrue();
        assertThat(instance.slot(r.slotOf()[instance.courseIndex("LAB1")]).session()).isEqualTo(Session.AFTERNOON);
    }

    @Test
    void slotCapacityIsRespected() {
        // Three independent 20-candidate papers and one 30-seat room: only one fits per slot.
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(3, 1))
                .room(Room.grid("R1", 5, 6));
        for (String code : new String[] {"A", "B", "C"}) {
            b.course(Course.of(code));
            for (int i = 0; i < 20; i++) {
                b.enrol(code + i, code);
            }
        }
        ColoringProblem p = problem(b.build());
        ColoringResult r = dsatur.color(p);
        assertThat(r.complete()).isTrue();
        assertThat(r.colorsUsed()).isEqualTo(3);
        assertProperColouring(p, r.slotOf());
    }

    @Test
    void deterministicForTheSameInput() {
        ProblemInstance instance = TestInstances.fromEdges(30, TestInstances.wheel(29), 30).build();
        assertThat(dsatur.color(problem(instance)).slotOf()).isEqualTo(dsatur.color(problem(instance)).slotOf());
    }

    private void assertExactColours(ProblemInstance instance, int expected) {
        ColoringProblem p = problem(instance);
        ColoringResult r = dsatur.color(p);
        assertThat(r.complete()).isTrue();
        assertThat(r.colorsUsed()).isEqualTo(expected);
        assertProperColouring(p, r.slotOf());
    }
}
