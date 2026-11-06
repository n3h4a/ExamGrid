package com.examgrid.solver.pipeline;

import com.examgrid.solver.coloring.ColoringProblem;
import com.examgrid.solver.coloring.InfeasibilityAnalyzer;
import com.examgrid.solver.coloring.InitialTimetable;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.optimize.Optimizer;
import com.examgrid.solver.optimize.OptimizerResult;
import com.examgrid.solver.optimize.PenaltyModel;
import com.examgrid.solver.optimize.Timetable;
import com.examgrid.solver.optimize.TimetableMetrics;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.ProgressListener;

/**
 * Builds the exam timetable: conflict graph → DSatur (with repair) → metaheuristic.
 * Throws {@link com.examgrid.solver.coloring.InfeasibleException} when no clash-free
 * timetable exists.
 */
public final class TimetableStage {

    private static final long REPAIR_ITERATIONS = 20_000;

    /** Everything later stages and reports need about the timetable. */
    public record Outcome(ExamGroups groups, ConflictGraph graph, ColoringProblem problem, PenaltyModel penalties,
                          int[] slotOfNode, int dsaturColours, boolean repaired, OptimizerResult optimisation,
                          TimetableMetrics metrics) {

        public Outcome {
            slotOfNode = slotOfNode.clone();
        }

        @Override
        public int[] slotOfNode() {
            return slotOfNode.clone();
        }

        public int slotOfCourse(int course) {
            return slotOfNode[groups.nodeOf(course)];
        }
    }

    public Outcome run(ProblemInstance instance, ProgressListener listener, CancellationToken cancel) {
        listener.onProgress(new ProgressEvent(Phase.GRAPH, Phase.GRAPH.overall(0), null, "building conflict graph"));
        ExamGroups groups = ExamGroups.of(instance);
        ConflictGraph graph = ConflictGraph.build(instance, groups);
        ColoringProblem problem = ColoringProblem.from(instance, graph);
        cancel.throwIfCancelled();

        listener.onProgress(new ProgressEvent(Phase.COLOURING, Phase.COLOURING.overall(0), null,
                graph.nodeCount() + " exams, " + graph.edgeCount() + " conflicts"));
        InitialTimetable.Result initial = new InitialTimetable(REPAIR_ITERATIONS, instance.params().seed())
                .build(new InfeasibilityAnalyzer(instance, graph, problem), problem);
        cancel.throwIfCancelled();

        PenaltyModel penalties = new PenaltyModel(instance.slots(), instance.params().weights());
        Timetable timetable = new Timetable(problem, penalties, initial.slotOf());
        listener.onProgress(new ProgressEvent(Phase.OPTIMISING, Phase.OPTIMISING.overall(0), timetable.cost(),
                "DSatur used " + initial.colorsUsed() + " slots"));
        OptimizerResult optimisation = Optimizer.forParams(instance.params()).optimise(timetable, listener, cancel);
        if (!timetable.isFeasible()) {
            throw new IllegalStateException("optimiser produced an infeasible timetable");
        }
        int[] slots = timetable.slots();
        TimetableMetrics metrics = TimetableMetrics.of(instance, groups, penalties, slots);
        listener.onProgress(new ProgressEvent(Phase.OPTIMISING, Phase.OPTIMISING.overall(100), metrics.score(),
                "timetable score " + metrics.score()));
        return new Outcome(groups, graph, problem, penalties, slots, initial.colorsUsed(), initial.repaired(),
                optimisation, metrics);
    }
}
