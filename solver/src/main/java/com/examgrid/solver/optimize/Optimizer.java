package com.examgrid.solver.optimize;

import com.examgrid.solver.model.OptimizerType;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.ProgressListener;

/** Improves the soft-constraint score of a feasible timetable without breaking hard constraints. */
public interface Optimizer {

    /**
     * @param timetable feasible starting point; left holding the best solution found
     * @param listener  receives progress within the optimisation phase
     * @param cancel    checked periodically
     */
    OptimizerResult optimise(Timetable timetable, ProgressListener listener, CancellationToken cancel);

    static Optimizer forParams(SolverParams params) {
        OptimizerType type = params.optimizer();
        return switch (type) {
            case ANNEALING -> new SimulatedAnnealing(params);
            case TABU -> new TabuSearch(params);
            case NONE -> (timetable, listener, cancel) -> new OptimizerResult(timetable.slots(), timetable.cost(),
                    timetable.cost(), 0, 0, 0, OptimizerResult.StopReason.DISABLED);
        };
    }
}
