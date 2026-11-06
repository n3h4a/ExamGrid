package com.examgrid.solver.model;

/**
 * Weights of the soft-constraint score. Every term is counted per pair of exams a student
 * sits, so a student with three papers on one day contributes three same-day pairs.
 *
 * <pre>
 * score = sameDay    × (exam pairs a student sits on the same day)
 *       + backToBack × (exam pairs in consecutive slots on consecutive days)
 *       + hardGap    × (pairs of hard papers less than hardMinGapDays apart)
 * </pre>
 */
public record PenaltyWeights(int sameDay, int backToBack, int hardGap, int hardMinGapDays) {

    public static final PenaltyWeights DEFAULT = new PenaltyWeights(10, 3, 5, 2);

    public PenaltyWeights {
        if (sameDay < 0 || backToBack < 0 || hardGap < 0) {
            throw new IllegalArgumentException("penalty weights must be non-negative");
        }
        if (hardMinGapDays < 0) {
            throw new IllegalArgumentException("hardMinGapDays must be non-negative");
        }
    }
}
