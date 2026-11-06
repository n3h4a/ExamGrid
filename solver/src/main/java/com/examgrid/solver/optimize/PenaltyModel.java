package com.examgrid.solver.optimize;

import com.examgrid.solver.model.PenaltyWeights;
import com.examgrid.solver.model.Slot;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pairwise soft-constraint costs between slots, precomputed as K × K matrices so the cost of
 * an edge (u, v) of the conflict graph is a table lookup:
 *
 * <pre>
 * cost(u, v) = students(u, v) · pair[s_u][s_v] + hardStudents(u, v) · hard[s_u][s_v]
 * </pre>
 *
 * <ul>
 *   <li>{@code pair} is {@code sameDay} when both slots fall on one date, {@code backToBack}
 *       when they are adjacent in slot order on consecutive calendar days (an afternoon paper
 *       followed by the next morning's), otherwise 0.</li>
 *   <li>{@code hard} is {@code hardGap} when the slots are fewer than {@code hardMinGapDays}
 *       calendar days apart, otherwise 0.</li>
 * </ul>
 *
 * Both matrices are symmetric, which is what makes swap and Kempe-chain deltas cheap.
 */
public final class PenaltyModel {

    private final int[][] pair;
    private final int[][] hard;
    private final boolean[][] sameDay;
    private final boolean[][] backToBack;
    private final boolean[][] hardGap;
    private final PenaltyWeights weights;

    public PenaltyModel(List<Slot> slots, PenaltyWeights weights) {
        int k = slots.size();
        this.weights = weights;
        pair = new int[k][k];
        hard = new int[k][k];
        sameDay = new boolean[k][k];
        backToBack = new boolean[k][k];
        hardGap = new boolean[k][k];
        for (int a = 0; a < k; a++) {
            for (int b = 0; b < k; b++) {
                if (a == b) {
                    continue;
                }
                long days = Math.abs(ChronoUnit.DAYS.between(slots.get(a).date(), slots.get(b).date()));
                sameDay[a][b] = days == 0;
                backToBack[a][b] = Math.abs(a - b) == 1 && days == 1;
                hardGap[a][b] = days < weights.hardMinGapDays();
                pair[a][b] = sameDay[a][b] ? weights.sameDay() : backToBack[a][b] ? weights.backToBack() : 0;
                hard[a][b] = hardGap[a][b] ? weights.hardGap() : 0;
            }
        }
    }

    public int pair(int slotA, int slotB) {
        return pair[slotA][slotB];
    }

    public int hard(int slotA, int slotB) {
        return hard[slotA][slotB];
    }

    public boolean sameDay(int slotA, int slotB) {
        return sameDay[slotA][slotB];
    }

    public boolean backToBack(int slotA, int slotB) {
        return backToBack[slotA][slotB];
    }

    public boolean hardGap(int slotA, int slotB) {
        return hardGap[slotA][slotB];
    }

    public PenaltyWeights weights() {
        return weights;
    }

    /** Cost contribution of one edge given its endpoint slots. */
    public long edgeCost(int students, int hardStudents, int slotA, int slotB) {
        return (long) students * pair[slotA][slotB] + (long) hardStudents * hard[slotA][slotB];
    }
}
