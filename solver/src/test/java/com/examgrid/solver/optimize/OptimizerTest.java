package com.examgrid.solver.optimize;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.OptimizerType;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.SolverCancelledException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimizerTest {

    private static final ProblemInstance SYNTHETIC =
            SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(120, 1500));

    @ParameterizedTest
    @EnumSource(value = OptimizerType.class, names = {"ANNEALING", "TABU"})
    void substantiallyImprovesTheDsaturTimetable(OptimizerType type) {
        Timetable tt = OptimizeFixtures.timetable(SYNTHETIC);
        long start = tt.cost();
        OptimizerResult r = Optimizer.forParams(params(type, 200_000)).optimise(tt, e -> { }, CancellationToken.none());
        assertThat(r.initialScore()).isEqualTo(start);
        assertThat(r.finalScore()).isLessThan(start / 10);
        assertThat(tt.isFeasible()).isTrue();
        assertThat(r.slotOf()).isEqualTo(tt.slots());
    }

    @Test
    void annealingIsDeterministicForAFixedSeed() {
        SolverParams params = params(OptimizerType.ANNEALING, 50_000);
        int[] a = run(params);
        int[] b = run(params);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void annealingStopsEarlyWhenItReachesZero() {
        ProblemInstance easy = TestInstances.specExample(9).build();
        Timetable tt = OptimizeFixtures.timetable(easy);
        assertThat(tt.cost()).isPositive();
        OptimizerResult r = new SimulatedAnnealing(params(OptimizerType.ANNEALING, 1_000_000))
                .optimise(tt, e -> { }, CancellationToken.none());
        assertThat(r.finalScore()).isZero();
        assertThat(r.stopReason()).isEqualTo(OptimizerResult.StopReason.OPTIMAL);
        assertThat(r.iterations()).isLessThan(1_000_000);
    }

    @ParameterizedTest
    @EnumSource(value = OptimizerType.class, names = {"ANNEALING", "TABU"})
    void honoursCancellation(OptimizerType type) {
        CancellationToken token = CancellationToken.none();
        token.cancel();
        Timetable tt = OptimizeFixtures.timetable(SYNTHETIC);
        assertThatThrownBy(() -> Optimizer.forParams(params(type, 100_000)).optimise(tt, e -> { }, token))
                .isInstanceOf(SolverCancelledException.class);
    }

    @Test
    void honoursTheTimeLimit() {
        SolverParams params = params(OptimizerType.ANNEALING, Long.MAX_VALUE / 2).toBuilder().timeLimitMillis(200).build();
        Timetable tt = OptimizeFixtures.timetable(SYNTHETIC);
        long started = System.nanoTime();
        OptimizerResult r = new SimulatedAnnealing(params).optimise(tt, e -> { }, CancellationToken.none());
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000);
        assertThat(r.stopReason()).isIn(OptimizerResult.StopReason.TIME_LIMIT, OptimizerResult.StopReason.OPTIMAL);
    }

    @Test
    void reportsProgressInsideTheOptimisingPhase() {
        List<ProgressEvent> events = new CopyOnWriteArrayList<>();
        SolverParams params = params(OptimizerType.ANNEALING, Long.MAX_VALUE / 2).toBuilder().timeLimitMillis(600).build();
        new SimulatedAnnealing(params).optimise(OptimizeFixtures.timetable(
                SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(400, 5000))),
                events::add, CancellationToken.none());
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> {
            assertThat(e.phase()).isEqualTo(Phase.OPTIMISING);
            assertThat(e.percent()).isBetween(Phase.OPTIMISING.overall(0), Phase.OPTIMISING.overall(100));
            assertThat(e.score()).isNotNull();
        });
    }

    @Test
    void disabledOptimiserReturnsTheStartUnchanged() {
        Timetable tt = OptimizeFixtures.timetable(SYNTHETIC);
        int[] before = tt.slots();
        OptimizerResult r = Optimizer.forParams(params(OptimizerType.NONE, 1)).optimise(tt, e -> { },
                CancellationToken.none());
        assertThat(r.slotOf()).isEqualTo(before);
        assertThat(r.stopReason()).isEqualTo(OptimizerResult.StopReason.DISABLED);
    }

    @Test
    void metricsCountTheSpecExampleByHand() {
        // Triangle CS301–MA201–PH101 forced into one day's three sessions.
        ProblemInstance withEnrolments = TestInstances.specExample(1).slots(TestInstances.slots(1, 3)).build();
        Timetable tt = OptimizeFixtures.timetable(withEnrolments);
        TimetableMetrics m = TimetableMetrics.of(withEnrolments, ExamGroups.of(withEnrolments), tt.penalties(),
                tt.slots());
        assertThat(m.clashPairs()).isZero();
        assertThat(m.sameDayPairs()).isEqualTo(3);        // Asha, Ravi, Meena: one pair each
        assertThat(m.studentsWithSameDay()).isEqualTo(3);
        assertThat(m.score()).isEqualTo(30);
        assertThat(m.maxExamsPerStudentDay()).isEqualTo(2);
    }

    private static int[] run(SolverParams params) {
        Timetable tt = OptimizeFixtures.timetable(SYNTHETIC);
        new SimulatedAnnealing(params).optimise(tt, e -> { }, CancellationToken.none());
        return tt.slots();
    }

    private static SolverParams params(OptimizerType type, long iterations) {
        return SolverParams.builder().optimizer(type).maxIterations(iterations).timeLimitMillis(60_000).seed(11).build();
    }
}
