package com.examgrid.solver.optimize;

import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.progress.CancellationToken;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableProperties {

    @Property(tries = 150)
    void incrementalCostMatchesFullRecomputationAndStaysFeasible(@ForAll long seed) {
        ProblemInstance instance = OptimizeFixtures.randomInstance(seed, 25, 60, 6);
        Timetable tt = OptimizeFixtures.timetable(instance);
        Assume.that(tt != null);
        SplittableRandom rnd = new SplittableRandom(seed);
        for (int i = 0; i < 400; i++) {
            int v = rnd.nextInt(tt.nodeCount());
            int kind = rnd.nextInt(3);
            long delta;
            if (kind == 0) {
                int t = rnd.nextInt(tt.slotCount());
                delta = tt.moveDelta(v, t);
                if (delta != Timetable.INFEASIBLE) {
                    tt.applyMove(v, t, delta);
                }
            } else if (kind == 1) {
                int b = rnd.nextInt(tt.nodeCount());
                delta = tt.swapDelta(v, b);
                if (delta != Timetable.INFEASIBLE) {
                    tt.applySwap(v, b, delta);
                }
            } else {
                int t = rnd.nextInt(tt.slotCount());
                delta = tt.kempeDelta(v, t);
                if (delta != Timetable.INFEASIBLE) {
                    tt.applyKempe(t, delta);
                }
            }
            assertThat(tt.cost()).isEqualTo(tt.fullCost());
            assertThat(tt.isFeasible()).isTrue();
        }
    }

    @Property(tries = 100)
    void graphScoreEqualsStudentLevelScore(@ForAll long seed) {
        ProblemInstance instance = OptimizeFixtures.randomInstance(seed, 20, 50, 5);
        Timetable tt = OptimizeFixtures.timetable(instance);
        Assume.that(tt != null);
        TimetableMetrics m = TimetableMetrics.of(instance, ExamGroups.of(instance), tt.penalties(), tt.slots());
        assertThat(m.score()).isEqualTo(tt.cost());
        assertThat(m.clashPairs()).isZero();
    }

    @Property(tries = 60)
    void optimisersNeverWorsenNorBreakHardConstraints(@ForAll long seed) {
        ProblemInstance instance = OptimizeFixtures.randomInstance(seed, 30, 80, 6);
        for (Optimizer optimizer : new Optimizer[] {
                new SimulatedAnnealing(instance.params().toBuilder().maxIterations(20_000).seed(seed).build()),
                new TabuSearch(instance.params().toBuilder().maxIterations(20_000).seed(seed).build())}) {
            Timetable tt = OptimizeFixtures.timetable(instance);
            Assume.that(tt != null);
            int[] start = tt.slots();
            OptimizerResult r = optimizer.optimise(tt, e -> { }, CancellationToken.none());
            assertThat(r.finalScore()).isLessThanOrEqualTo(r.initialScore());
            assertThat(r.finalScore()).isEqualTo(tt.fullCost());
            assertThat(tt.isFeasible()).isTrue();
            for (int v = 0; v < tt.nodeCount(); v++) {
                if (tt.isLocked(v)) {
                    assertThat(tt.slotOf(v)).isEqualTo(start[v]);
                }
            }
        }
    }
}
