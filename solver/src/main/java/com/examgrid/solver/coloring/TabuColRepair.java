package com.examgrid.solver.coloring;

import com.examgrid.solver.graph.ConflictGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * TabuCol (Hertz &amp; de Werra, 1987) used as a repair step when greedy DSatur runs out of
 * slots. DSatur is a heuristic, so "DSatur failed" does not prove that no timetable exists;
 * this search keeps trying within the same K slots.
 *
 * <p>Every node gets a slot from its domain, then the search minimises
 * {@code 1000 × clashing edges + seats over capacity}. Each iteration moves one non-locked
 * node involved in a violation to the best slot that is not tabu (aspiration: a tabu move is
 * allowed if it beats the best value seen). The move back is tabu for
 * {@code random(10) + 0.6 × violating nodes} iterations, the tenure recommended by Galinier
 * and Hao.
 */
public final class TabuColRepair {

    private static final long CLASH_WEIGHT = 1000;

    private final long maxIterations;
    private final long seed;

    public TabuColRepair(long maxIterations, long seed) {
        this.maxIterations = maxIterations;
        this.seed = seed;
    }

    public ColoringResult repair(ColoringProblem problem, ColoringResult partial) {
        ConflictGraph g = problem.graph();
        int n = g.nodeCount();
        int k = problem.slotCount();
        SplittableRandom random = new SplittableRandom(seed);

        int[] slotOf = partial.slotOf();
        int[][] neighbourCount = new int[n][k];
        long[] load = new long[k];
        for (int v = 0; v < n; v++) {
            if (slotOf[v] >= 0) {
                place(g, problem, v, slotOf[v], neighbourCount, load);
            }
        }
        for (int v : partial.uncolored()) {
            if (problem.domainSize(v) == 0) {
                return partial;
            }
            int best = -1;
            long bestCost = Long.MAX_VALUE;
            for (int s = 0; s < k; s++) {
                if (!problem.allowed(v, s)) {
                    continue;
                }
                long cost = CLASH_WEIGHT * neighbourCount[v][s]
                        + Math.max(0, load[s] + problem.nodeSize(v) - problem.slotCapacity(s));
                if (cost < bestCost) {
                    bestCost = cost;
                    best = s;
                }
            }
            slotOf[v] = best;
            place(g, problem, v, best, neighbourCount, load);
        }

        long current = objective(g, problem, slotOf, load);
        long best = current;
        int[] bestSlots = slotOf.clone();
        long[][] tabuUntil = new long[n][k];

        for (long iter = 0; iter < maxIterations && current > 0; iter++) {
            List<Integer> candidates = violatingNodes(g, problem, slotOf, load);
            if (candidates.isEmpty()) {
                break;
            }
            int moveNode = -1;
            int moveSlot = -1;
            long moveDelta = Long.MAX_VALUE;
            int ties = 0;
            for (int v : candidates) {
                int from = slotOf[v];
                for (int s = 0; s < k; s++) {
                    if (s == from || !problem.allowed(v, s)) {
                        continue;
                    }
                    long delta = delta(problem, v, from, s, neighbourCount, load);
                    boolean tabu = tabuUntil[v][s] > iter;
                    if (tabu && current + delta >= best) {
                        continue;
                    }
                    if (delta < moveDelta) {
                        moveDelta = delta;
                        moveNode = v;
                        moveSlot = s;
                        ties = 1;
                    } else if (delta == moveDelta && random.nextInt(++ties) == 0) {
                        moveNode = v;
                        moveSlot = s;
                    }
                }
            }
            if (moveNode < 0) {
                continue;
            }
            int from = slotOf[moveNode];
            unplace(g, problem, moveNode, from, neighbourCount, load);
            slotOf[moveNode] = moveSlot;
            place(g, problem, moveNode, moveSlot, neighbourCount, load);
            tabuUntil[moveNode][from] = iter + random.nextInt(10) + (long) (0.6 * candidates.size()) + 1;
            current += moveDelta;
            if (current < best) {
                best = current;
                bestSlots = slotOf.clone();
            }
        }

        if (best > 0) {
            return partial;
        }
        return new ColoringResult(bestSlots, List.of());
    }

    private static long delta(ColoringProblem p, int v, int from, int to, int[][] nc, long[] load) {
        long clashDelta = CLASH_WEIGHT * (nc[v][to] - nc[v][from]);
        long size = p.nodeSize(v);
        long overFromBefore = Math.max(0, load[from] - p.slotCapacity(from));
        long overFromAfter = Math.max(0, load[from] - size - p.slotCapacity(from));
        long overToBefore = Math.max(0, load[to] - p.slotCapacity(to));
        long overToAfter = Math.max(0, load[to] + size - p.slotCapacity(to));
        return clashDelta + (overFromAfter - overFromBefore) + (overToAfter - overToBefore);
    }

    private static long objective(ConflictGraph g, ColoringProblem p, int[] slotOf, long[] load) {
        long clashes = 0;
        for (int v = 0; v < g.nodeCount(); v++) {
            for (int u : g.neighbours(v)) {
                if (u > v && slotOf[u] == slotOf[v]) {
                    clashes++;
                }
            }
        }
        long over = 0;
        for (int s = 0; s < p.slotCount(); s++) {
            over += Math.max(0, load[s] - p.slotCapacity(s));
        }
        return CLASH_WEIGHT * clashes + over;
    }

    private static List<Integer> violatingNodes(ConflictGraph g, ColoringProblem p, int[] slotOf, long[] load) {
        boolean[] overloaded = new boolean[p.slotCount()];
        for (int s = 0; s < p.slotCount(); s++) {
            overloaded[s] = load[s] > p.slotCapacity(s);
        }
        List<Integer> out = new ArrayList<>();
        for (int v = 0; v < g.nodeCount(); v++) {
            if (p.isLocked(v)) {
                continue;
            }
            boolean violating = overloaded[slotOf[v]];
            if (!violating) {
                for (int u : g.neighbours(v)) {
                    if (slotOf[u] == slotOf[v]) {
                        violating = true;
                        break;
                    }
                }
            }
            if (violating) {
                out.add(v);
            }
        }
        return out;
    }

    private static void place(ConflictGraph g, ColoringProblem p, int v, int s, int[][] nc, long[] load) {
        load[s] += p.nodeSize(v);
        for (int u : g.neighbours(v)) {
            nc[u][s]++;
        }
    }

    private static void unplace(ConflictGraph g, ColoringProblem p, int v, int s, int[][] nc, long[] load) {
        load[s] -= p.nodeSize(v);
        for (int u : g.neighbours(v)) {
            nc[u][s]--;
        }
    }

    /** Exposed for tests: true when no edge is monochromatic and no slot is over capacity. */
    public static boolean isFeasible(ColoringProblem p, int[] slotOf) {
        long[] load = new long[p.slotCount()];
        for (int v = 0; v < slotOf.length; v++) {
            if (slotOf[v] < 0 || !p.allowed(v, slotOf[v])) {
                return false;
            }
            load[slotOf[v]] += p.nodeSize(v);
        }
        return objective(p.graph(), p, slotOf, load) == 0 && Arrays.stream(slotOf).allMatch(s -> s >= 0);
    }
}
