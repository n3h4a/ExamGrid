package com.examgrid.backend.scheduling;

import com.examgrid.solver.model.OptimizerType;
import com.examgrid.solver.model.PenaltyWeights;
import com.examgrid.solver.model.SolverParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/schedule-runs}. Every field is optional; omitted fields take the
 * solver defaults, and the resolved values are stored with the run so it can be reproduced.
 */
public record RunParams(
        @Size(max = 120) String label,
        OptimizerType optimizer,
        @Min(0) @Max(20_000_000) Long maxIterations,
        @Min(1) @Max(600) Integer timeLimitSeconds,
        Long seed,
        @Valid Weights weights,
        @Min(1) @Max(500) Integer studentsPerInvigilator,
        @Min(1) @Max(10) Integer minInvigilatorsPerRoom,
        Boolean diagonalAdjacency) {

    /** Penalty weights; see ADR-0003. */
    public record Weights(@Min(0) @Max(1000) Integer sameDay, @Min(0) @Max(1000) Integer backToBack,
                          @Min(0) @Max(1000) Integer hardGap, @Min(0) @Max(30) Integer hardMinGapDays) {
    }

    public static final long DEFAULT_ITERATIONS = 1_000_000;
    public static final int DEFAULT_TIME_LIMIT_SECONDS = 60;

    /** Every field filled in with its effective value. */
    public RunParams resolved() {
        SolverParams d = SolverParams.defaults();
        PenaltyWeights w = d.weights();
        Weights given = weights == null ? new Weights(null, null, null, null) : weights;
        return new RunParams(
                label == null || label.isBlank() ? null : label.strip(),
                optimizer == null ? d.optimizer() : optimizer,
                maxIterations == null ? DEFAULT_ITERATIONS : maxIterations,
                timeLimitSeconds == null ? DEFAULT_TIME_LIMIT_SECONDS : timeLimitSeconds,
                seed == null ? d.seed() : seed,
                new Weights(
                        given.sameDay() == null ? w.sameDay() : given.sameDay(),
                        given.backToBack() == null ? w.backToBack() : given.backToBack(),
                        given.hardGap() == null ? w.hardGap() : given.hardGap(),
                        given.hardMinGapDays() == null ? w.hardMinGapDays() : given.hardMinGapDays()),
                studentsPerInvigilator == null ? d.studentsPerInvigilator() : studentsPerInvigilator,
                minInvigilatorsPerRoom == null ? d.minInvigilatorsPerRoom() : minInvigilatorsPerRoom,
                diagonalAdjacency != null && diagonalAdjacency);
    }

    /** Solver parameters of a resolved request. */
    public SolverParams toSolverParams() {
        RunParams r = resolved();
        return SolverParams.builder()
                .optimizer(r.optimizer())
                .maxIterations(r.maxIterations())
                .timeLimitMillis(r.timeLimitSeconds() * 1000L)
                .seed(r.seed())
                .weights(new PenaltyWeights(r.weights().sameDay(), r.weights().backToBack(), r.weights().hardGap(),
                        r.weights().hardMinGapDays()))
                .studentsPerInvigilator(r.studentsPerInvigilator())
                .minInvigilatorsPerRoom(r.minInvigilatorsPerRoom())
                .diagonalAdjacency(r.diagonalAdjacency())
                .build();
    }
}
