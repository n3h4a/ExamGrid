package com.examgrid.solver.coloring;

import com.examgrid.solver.graph.ConflictGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * DSatur (Brélaz, 1979) generalised to per-node slot domains and slot capacities.
 *
 * <p>The next node to colour is the most constrained one: the fewest slots still open to it
 * (with uniform domains this is exactly "highest saturation"), then the highest saturation,
 * then the highest degree, then the lowest index so the result is deterministic. It gets the
 * lowest-numbered slot that is in its domain, not used by a neighbour, and has room for its
 * students.
 *
 * <p>The priority queue is a {@link TreeSet} keyed on that ordering. When a node is coloured
 * only its uncoloured neighbours change priority; each is removed and reinserted, giving
 * O((n + m) log n) overall plus O(K) per colour choice.
 *
 * <p>Locked nodes are coloured first. A node with no open slot is left uncoloured and
 * reported; the caller can hand the partial colouring to {@link TabuColRepair}.
 */
public final class DSaturColoring {

    public ColoringResult color(ColoringProblem problem) {
        ConflictGraph g = problem.graph();
        int n = g.nodeCount();
        int k = problem.slotCount();

        int[] slotOf = new int[n];
        Arrays.fill(slotOf, -1);
        // neighbourCount[v][s]: neighbours of v already in slot s. A slot is open to v when it
        // is in v's domain and this count is zero.
        int[][] neighbourCount = new int[n][k];
        int[] saturation = new int[n];
        int[] open = new int[n];
        for (int v = 0; v < n; v++) {
            open[v] = problem.domainSize(v);
        }
        long[] load = new long[k];
        List<Integer> uncolored = new ArrayList<>();
        boolean[] done = new boolean[n];

        Comparator<Integer> mostConstrainedFirst = Comparator
                .<Integer>comparingInt(v -> open[v])
                .thenComparing(v -> -saturation[v])
                .thenComparing(v -> -g.degree(v))
                .thenComparing(v -> v);
        TreeSet<Integer> queue = new TreeSet<>(mostConstrainedFirst);

        // Phase 1: honour locks.
        for (int v = 0; v < n; v++) {
            if (!problem.isLocked(v)) {
                continue;
            }
            int s = problem.lockedSlot(v);
            boolean fits = problem.allowed(v, s) && neighbourCount[v][s] == 0
                    && load[s] + problem.nodeSize(v) <= problem.slotCapacity(s);
            done[v] = true;
            if (fits) {
                assign(problem, v, s, slotOf, neighbourCount, saturation, open, load, done, null);
            } else {
                uncolored.add(v);
            }
        }

        // Phase 2: DSatur over the rest.
        for (int v = 0; v < n; v++) {
            if (!done[v]) {
                queue.add(v);
            }
        }
        while (!queue.isEmpty()) {
            int v = queue.pollFirst();
            done[v] = true;
            int chosen = -1;
            for (int s = 0; s < k; s++) {
                if (problem.allowed(v, s) && neighbourCount[v][s] == 0
                        && load[s] + problem.nodeSize(v) <= problem.slotCapacity(s)) {
                    chosen = s;
                    break;
                }
            }
            if (chosen < 0) {
                uncolored.add(v);
                continue;
            }
            assign(problem, v, chosen, slotOf, neighbourCount, saturation, open, load, done, queue);
        }
        return new ColoringResult(slotOf, uncolored);
    }

    private static void assign(ColoringProblem problem, int v, int s, int[] slotOf, int[][] neighbourCount,
                               int[] saturation, int[] open, long[] load, boolean[] done,
                               TreeSet<Integer> queue) {
        slotOf[v] = s;
        load[s] += problem.nodeSize(v);
        for (int u : problem.graph().neighbours(v)) {
            if (neighbourCount[u][s]++ == 0) {
                boolean queued = queue != null && !done[u];
                if (queued) {
                    queue.remove(u);
                }
                saturation[u]++;
                if (problem.allowed(u, s)) {
                    open[u]--;
                }
                if (queued) {
                    queue.add(u);
                }
            }
        }
    }
}
