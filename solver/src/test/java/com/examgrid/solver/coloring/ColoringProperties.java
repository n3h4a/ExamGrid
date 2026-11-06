package com.examgrid.solver.coloring;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Session;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.examgrid.solver.coloring.ColoringFixtures.analyzer;
import static com.examgrid.solver.coloring.ColoringFixtures.problem;
import static org.assertj.core.api.Assertions.assertThat;

class ColoringProperties {

    @Property(tries = 300)
    void dsaturNeverUsesMoreThanMaxDegreePlusOneColours(@ForAll("graphs") int[][] edges,
                                                        @ForAll @IntRange(min = 2, max = 30) int n) {
        ProblemInstance instance = TestInstances.fromEdges(n, clip(edges, n), n).build();
        ColoringProblem p = problem(instance);
        ColoringResult r = new DSaturColoring().color(p);
        assertThat(r.complete()).isTrue();
        assertThat(TabuColRepair.isFeasible(p, r.slotOf())).isTrue();
        assertThat(r.colorsUsed()).isLessThanOrEqualTo(p.graph().maxDegree() + 1);
    }

    @Property(tries = 200)
    void dsaturIsExactOnBipartiteGraphs(@ForAll("graphs") int[][] edges,
                                        @ForAll @IntRange(min = 2, max = 30) int n) {
        // Keep only edges between even and odd vertices: the graph is bipartite by construction.
        int[][] bipartite = java.util.Arrays.stream(clip(edges, n))
                .filter(e -> (e[0] + e[1]) % 2 == 1)
                .toArray(int[][]::new);
        ProblemInstance instance = TestInstances.fromEdges(n, bipartite, n).build();
        ColoringResult r = new DSaturColoring().color(problem(instance));
        assertThat(r.colorsUsed()).isLessThanOrEqualTo(bipartite.length == 0 ? 1 : 2);
    }

    @Property(tries = 200)
    void initialTimetableIsValidOrExplained(@ForAll("instances") ProblemInstance instance) {
        ColoringProblem p = problem(instance);
        try {
            InitialTimetable.Result r = new InitialTimetable(2_000, 1).build(analyzer(instance, p), p);
            assertThat(TabuColRepair.isFeasible(p, r.slotOf())).isTrue();
            assertNoStudentClash(instance, p.graph(), r.slotOf());
        } catch (InfeasibleException e) {
            assertThat(e.report().issues()).isNotEmpty();
            assertThat(e.report().issues()).allSatisfy(i -> assertThat(i.message()).isNotBlank());
        }
    }

    private static void assertNoStudentClash(ProblemInstance instance, ConflictGraph g, int[] slotOfNode) {
        for (int s = 0; s < instance.studentCount(); s++) {
            Set<Integer> used = new java.util.HashSet<>();
            for (int c : instance.coursesOf(s)) {
                assertThat(used.add(slotOfNode[g.groups().nodeOf(c)]))
                        .as("student %s has two exams in one slot", instance.student(s)).isTrue();
            }
        }
    }

    private static int[][] clip(int[][] edges, int n) {
        return java.util.Arrays.stream(edges)
                .map(e -> new int[] {e[0] % n, e[1] % n})
                .filter(e -> e[0] != e[1])
                .toArray(int[][]::new);
    }

    @Provide
    Arbitrary<int[][]> graphs() {
        Arbitrary<int[]> edge = Combinators.combine(Arbitraries.integers().between(0, 29),
                Arbitraries.integers().between(0, 29)).as((a, b) -> new int[] {a, b});
        return edge.list().ofMaxSize(120).map(l -> l.toArray(int[][]::new));
    }

    @Provide
    Arbitrary<ProblemInstance> instances() {
        Arbitrary<Integer> courses = Arbitraries.integers().between(2, 25);
        Arbitrary<Integer> students = Arbitraries.integers().between(1, 60);
        Arbitrary<Integer> slots = Arbitraries.integers().between(2, 12);
        Arbitrary<Long> seeds = Arbitraries.longs();
        return Combinators.combine(courses, students, slots, seeds).as((c, s, k, seed) -> {
            java.util.SplittableRandom rnd = new java.util.SplittableRandom(seed);
            ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(k, 2));
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < c; i++) {
                Set<Session> sessions = rnd.nextInt(5) == 0 ? Set.of(Session.AFTERNOON) : Set.of();
                b.course(new Course("C" + i, "", rnd.nextBoolean(), sessions));
                codes.add("C" + i);
            }
            for (int st = 0; st < s; st++) {
                int load = 1 + rnd.nextInt(Math.min(6, c));
                for (int j = 0; j < load; j++) {
                    b.enrol("S" + st, codes.get(rnd.nextInt(c)));
                }
            }
            if (rnd.nextInt(3) == 0) {
                b.lock(codes.get(rnd.nextInt(c)), TestInstances.slots(k, 2).get(rnd.nextInt(2 * k)).id());
            }
            return b.build();
        });
    }
}
