package com.examgrid.solver.model;

import java.util.Objects;

/**
 * Tuning knobs for one solve. Instances are immutable; use {@link #builder()} or
 * {@link #toBuilder()} to derive variants.
 */
public record SolverParams(
        PenaltyWeights weights,
        OptimizerType optimizer,
        long maxIterations,
        long timeLimitMillis,
        double coolingRate,
        double initialAcceptance,
        long seed,
        int studentsPerInvigilator,
        int minInvigilatorsPerRoom,
        boolean diagonalAdjacency) {

    public SolverParams {
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(optimizer, "optimizer");
        if (maxIterations < 0) {
            throw new IllegalArgumentException("maxIterations must be >= 0");
        }
        if (timeLimitMillis <= 0) {
            throw new IllegalArgumentException("timeLimitMillis must be > 0");
        }
        if (!(coolingRate > 0 && coolingRate < 1)) {
            throw new IllegalArgumentException("coolingRate must be in (0, 1)");
        }
        if (!(initialAcceptance > 0 && initialAcceptance < 1)) {
            throw new IllegalArgumentException("initialAcceptance must be in (0, 1)");
        }
        Validation.requirePositive(studentsPerInvigilator, "studentsPerInvigilator");
        if (minInvigilatorsPerRoom < 1) {
            throw new IllegalArgumentException("minInvigilatorsPerRoom must be >= 1");
        }
    }

    public static SolverParams defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .weights(weights)
                .optimizer(optimizer)
                .maxIterations(maxIterations)
                .timeLimitMillis(timeLimitMillis)
                .coolingRate(coolingRate)
                .initialAcceptance(initialAcceptance)
                .seed(seed)
                .studentsPerInvigilator(studentsPerInvigilator)
                .minInvigilatorsPerRoom(minInvigilatorsPerRoom)
                .diagonalAdjacency(diagonalAdjacency);
    }

    public static final class Builder {
        private PenaltyWeights weights = PenaltyWeights.DEFAULT;
        private OptimizerType optimizer = OptimizerType.ANNEALING;
        private long maxIterations = 300_000;
        private long timeLimitMillis = 30_000;
        private double coolingRate = 0.995;
        private double initialAcceptance = 0.8;
        private long seed = 42L;
        private int studentsPerInvigilator = 30;
        private int minInvigilatorsPerRoom = 1;
        private boolean diagonalAdjacency = false;

        private Builder() {
        }

        public Builder weights(PenaltyWeights v) { this.weights = v; return this; }
        public Builder optimizer(OptimizerType v) { this.optimizer = v; return this; }
        public Builder maxIterations(long v) { this.maxIterations = v; return this; }
        public Builder timeLimitMillis(long v) { this.timeLimitMillis = v; return this; }
        public Builder coolingRate(double v) { this.coolingRate = v; return this; }
        public Builder initialAcceptance(double v) { this.initialAcceptance = v; return this; }
        public Builder seed(long v) { this.seed = v; return this; }
        public Builder studentsPerInvigilator(int v) { this.studentsPerInvigilator = v; return this; }
        public Builder minInvigilatorsPerRoom(int v) { this.minInvigilatorsPerRoom = v; return this; }
        public Builder diagonalAdjacency(boolean v) { this.diagonalAdjacency = v; return this; }

        public SolverParams build() {
            return new SolverParams(weights, optimizer, maxIterations, timeLimitMillis, coolingRate,
                    initialAcceptance, seed, studentsPerInvigilator, minInvigilatorsPerRoom, diagonalAdjacency);
        }
    }
}
