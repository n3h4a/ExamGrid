package com.examgrid.solver.pipeline;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.SolverCancelledException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimetableStageTest {

    @Test
    void producesAClashFreeOptimisedTimetableAndReportsPhasesInOrder() {
        ProblemInstance instance = SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(120, 1500));
        List<ProgressEvent> events = new ArrayList<>();
        TimetableStage.Outcome outcome = new TimetableStage().run(instance, events::add, CancellationToken.none());

        assertThat(outcome.metrics().clashPairs()).isZero();
        assertThat(outcome.metrics().score()).isEqualTo(outcome.optimisation().finalScore());
        assertThat(outcome.optimisation().finalScore()).isLessThanOrEqualTo(outcome.optimisation().initialScore());
        assertThat(events.getFirst().phase()).isEqualTo(Phase.GRAPH);
        assertThat(events.getLast().phase()).isEqualTo(Phase.OPTIMISING);
        assertThat(events).extracting(ProgressEvent::percent).isSorted();
        for (int c = 0; c < instance.courseCount(); c++) {
            assertThat(outcome.slotOfCourse(c)).isBetween(0, instance.slotCount() - 1);
        }
    }

    @Test
    void lockedCourseKeepsItsSlotThroughOptimisation() {
        ProblemInstance base = SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(60, 500));
        String course = base.course(5).code();
        String slot = base.slot(7).id();
        ProblemInstance instance = base.toBuilder().lock(course, slot).build();
        TimetableStage.Outcome outcome = new TimetableStage().run(instance, e -> { }, CancellationToken.none());
        assertThat(outcome.slotOfCourse(instance.courseIndex(course))).isEqualTo(instance.slotIndex(slot));
    }

    @Test
    void propagatesInfeasibility() {
        assertThatThrownBy(() -> new TimetableStage().run(TestInstances.specExample(2).build(), e -> { },
                CancellationToken.none())).isInstanceOf(InfeasibleException.class);
    }

    @Test
    void propagatesCancellation() {
        CancellationToken token = CancellationToken.none();
        token.cancel();
        assertThatThrownBy(() -> new TimetableStage().run(TestInstances.specExample(3).build(), e -> { }, token))
                .isInstanceOf(SolverCancelledException.class);
    }
}
