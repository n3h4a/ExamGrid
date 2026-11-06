package com.examgrid.solver.graph;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictGraphTest {

    @Test
    void specExampleFormsATriangleWithAnIsolatedCourse() {
        ProblemInstance instance = TestInstances.specExample(3).build();
        ExamGroups groups = ExamGroups.of(instance);
        ConflictGraph g = ConflictGraph.build(instance, groups);

        int cs = groups.nodeOf(instance.courseIndex("CS301"));
        int ma = groups.nodeOf(instance.courseIndex("MA201"));
        int ph = groups.nodeOf(instance.courseIndex("PH101"));
        int ee = groups.nodeOf(instance.courseIndex("EE210"));

        assertThat(g.nodeCount()).isEqualTo(4);
        assertThat(g.edgeCount()).isEqualTo(3);
        assertThat(g.adjacent(cs, ma)).isTrue();
        assertThat(g.adjacent(cs, ph)).isTrue();
        assertThat(g.adjacent(ma, ph)).isTrue();
        assertThat(g.degree(ee)).isZero();
        assertThat(g.weight(cs, ma)).isEqualTo(1);
        assertThat(g.nodeSize(cs)).isEqualTo(2);
    }

    @Test
    void edgeWeightCountsSharedStudentsAndAdjacencyIsSorted() {
        ProblemInstance instance = TestInstances.fromEdges(4, new int[][] {{0, 3}, {0, 3}, {0, 1}, {2, 0}}, 4).build();
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        assertThat(g.neighbours(0)).containsExactly(1, 2, 3);
        assertThat(g.weight(0, 3)).isEqualTo(2);
        assertThat(g.weight(3, 0)).isEqualTo(2);
        assertThat(g.weight(1, 2)).isZero();
        assertThat(g.maxDegree()).isEqualTo(3);
    }

    @Test
    void hardWeightCountsOnlyStudentsSittingHardPapersOnBothSides() {
        ProblemInstance instance = ProblemInstance.builder()
                .course(Course.hard("MA101")).course(Course.hard("PH101")).course(Course.of("HS101"))
                .enrol("S1", "MA101").enrol("S1", "PH101").enrol("S1", "HS101")
                .enrol("S2", "MA101").enrol("S2", "HS101")
                .slots(TestInstances.slots(3, 1))
                .build();
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        int ma = instance.courseIndex("MA101");
        int ph = instance.courseIndex("PH101");
        int hs = instance.courseIndex("HS101");
        int iMaPh = java.util.Arrays.binarySearch(g.neighbours(ma), ph);
        int iMaHs = java.util.Arrays.binarySearch(g.neighbours(ma), hs);
        assertThat(g.hardWeights(ma)[iMaPh]).isEqualTo(1);
        assertThat(g.hardWeights(ma)[iMaHs]).isZero();
        assertThat(g.weights(ma)[iMaHs]).isEqualTo(2);
    }

    @Test
    void sameSlotGroupsCollapseIntoOneNode() {
        ProblemInstance instance = ProblemInstance.builder()
                .course(Course.of("CS201")).course(Course.of("IT201")).course(Course.of("MA201"))
                .enrol("A", "CS201").enrol("A", "MA201")
                .enrol("B", "IT201").enrol("B", "MA201")
                .sameSlot(Set.of("CS201", "IT201"))
                .slots(TestInstances.slots(2, 1))
                .build();
        ExamGroups groups = ExamGroups.of(instance);
        ConflictGraph g = ConflictGraph.build(instance, groups);
        int common = groups.nodeOf(instance.courseIndex("CS201"));
        assertThat(groups.nodeOf(instance.courseIndex("IT201"))).isEqualTo(common);
        assertThat(g.nodeCount()).isEqualTo(2);
        assertThat(g.weight(common, groups.nodeOf(instance.courseIndex("MA201")))).isEqualTo(2);
        assertThat(g.nodeSize(common)).isEqualTo(2);
        assertThat(g.internalConflicts()).isEmpty();
    }

    @Test
    void studentSittingTwoCoursesOfOneGroupIsAnInternalConflict() {
        ProblemInstance instance = ProblemInstance.builder()
                .course(Course.of("CS201")).course(Course.of("IT201"))
                .enrol("A", "CS201").enrol("A", "IT201")
                .sameSlot(Set.of("CS201", "IT201"))
                .slots(TestInstances.slots(2, 1))
                .build();
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        assertThat(g.internalConflicts()).hasSize(1);
        assertThat(g.edgeCount()).isZero();
    }

    @Test
    void sharedStudentsReturnsSortedWitnesses() {
        ProblemInstance instance = TestInstances.specExample(3).enrol("ZED", "CS301").enrol("ZED", "MA201").build();
        ExamGroups groups = ExamGroups.of(instance);
        int[] shared = ConflictGraph.sharedStudents(instance, groups,
                instance.courseIndex("CS301"), instance.courseIndex("MA201"), 5);
        assertThat(java.util.Arrays.stream(shared).mapToObj(instance::student)).containsExactly("ASHA", "ZED");
    }

    @Test
    void cliqueFinderFindsTheTriangle() {
        ProblemInstance instance = TestInstances.specExample(3).build();
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        assertThat(CliqueFinder.largestClique(g)).hasSize(3);
    }

    @Test
    void cliqueFinderFindsK5InsideNoise() {
        int[][] k5 = TestInstances.complete(5);
        int[][] edges = java.util.Arrays.copyOf(k5, k5.length + 3);
        edges[k5.length] = new int[] {4, 5};
        edges[k5.length + 1] = new int[] {5, 6};
        edges[k5.length + 2] = new int[] {6, 7};
        ProblemInstance instance = TestInstances.fromEdges(8, edges, 5).build();
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        assertThat(CliqueFinder.largestClique(g)).containsExactly(0, 1, 2, 3, 4);
    }
}
