package com.examgrid.solver.optimize;

import com.examgrid.solver.coloring.ColoringProblem;
import com.examgrid.solver.graph.ConflictGraph;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Mutable timetable (exam node → slot) with an incrementally maintained cost and per-slot
 * seat load. Every mutator keeps the hard constraints: no edge inside a slot, every node in
 * its domain, locked nodes untouched and no slot over capacity. Moves that would break one
 * are reported as infeasible by the matching {@code can…}/{@code …Delta} method instead.
 *
 * <p>Neighbourhoods:
 * <ul>
 *   <li><b>move</b> one node to another slot — O(deg);</li>
 *   <li><b>swap</b> the slots of two nodes — O(deg a + deg b);</li>
 *   <li><b>Kempe chain</b>: the connected component of the subgraph induced by slots s and t
 *       containing a node, with s and t exchanged. Always clash-free, which makes it the
 *       strongest timetabling move; O(chain · deg).</li>
 * </ul>
 */
public final class Timetable {

    /** Returned by delta methods when the move would break a hard constraint. */
    public static final long INFEASIBLE = Long.MAX_VALUE;

    private final ColoringProblem problem;
    private final ConflictGraph graph;
    private final PenaltyModel penalties;
    private final int[] slotOf;
    private final long[] load;
    private long cost;

    // Scratch space for Kempe chains.
    private final int[] chainMark;
    private int chainStamp;
    private int[] chain = new int[16];
    private int chainSize;

    public Timetable(ColoringProblem problem, PenaltyModel penalties, int[] slotOfNode) {
        this.problem = problem;
        this.graph = problem.graph();
        this.penalties = penalties;
        this.slotOf = slotOfNode.clone();
        this.load = new long[problem.slotCount()];
        this.chainMark = new int[graph.nodeCount()];
        for (int v = 0; v < slotOf.length; v++) {
            if (slotOf[v] < 0 || slotOf[v] >= problem.slotCount()) {
                throw new IllegalArgumentException("node " + v + " has no slot");
            }
            load[slotOf[v]] += problem.nodeSize(v);
        }
        this.cost = fullCost();
    }

    public Timetable copy() {
        return new Timetable(problem, penalties, slotOf);
    }

    public int nodeCount() { return slotOf.length; }
    public int slotCount() { return problem.slotCount(); }
    public int slotOf(int node) { return slotOf[node]; }
    public int[] slots() { return slotOf.clone(); }
    public long cost() { return cost; }
    public boolean isLocked(int node) { return problem.isLocked(node); }
    public ColoringProblem problem() { return problem; }
    public PenaltyModel penalties() { return penalties; }

    /** Recomputes the cost from scratch in O(n + m); used to verify incremental updates. */
    public long fullCost() {
        long total = 0;
        for (int v = 0; v < slotOf.length; v++) {
            int[] nb = graph.neighbours(v);
            int[] w = graph.weights(v);
            int[] h = graph.hardWeights(v);
            for (int i = 0; i < nb.length; i++) {
                if (nb[i] > v) {
                    total += penalties.edgeCost(w[i], h[i], slotOf[v], slotOf[nb[i]]);
                }
            }
        }
        return total;
    }

    /** True when the timetable satisfies every hard constraint. */
    public boolean isFeasible() {
        long[] l = new long[problem.slotCount()];
        for (int v = 0; v < slotOf.length; v++) {
            int s = slotOf[v];
            if (!problem.allowed(v, s) || (problem.isLocked(v) && problem.lockedSlot(v) != s)) {
                return false;
            }
            for (int u : graph.neighbours(v)) {
                if (slotOf[u] == s) {
                    return false;
                }
            }
            l[s] += problem.nodeSize(v);
        }
        for (int s = 0; s < l.length; s++) {
            if (l[s] > problem.slotCapacity(s)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- move

    /** Cost change of moving {@code v} to slot {@code t}, or {@link #INFEASIBLE}. */
    public long moveDelta(int v, int t) {
        int s = slotOf[v];
        if (s == t || problem.isLocked(v) || !problem.allowed(v, t)
                || load[t] + problem.nodeSize(v) > problem.slotCapacity(t)) {
            return INFEASIBLE;
        }
        int[] nb = graph.neighbours(v);
        int[] w = graph.weights(v);
        int[] h = graph.hardWeights(v);
        long delta = 0;
        for (int i = 0; i < nb.length; i++) {
            int su = slotOf[nb[i]];
            if (su == t) {
                return INFEASIBLE;
            }
            delta += penalties.edgeCost(w[i], h[i], t, su) - penalties.edgeCost(w[i], h[i], s, su);
        }
        return delta;
    }

    /** Applies a move previously checked with {@link #moveDelta}. */
    public void applyMove(int v, int t, long delta) {
        int s = slotOf[v];
        load[s] -= problem.nodeSize(v);
        load[t] += problem.nodeSize(v);
        slotOf[v] = t;
        cost += delta;
    }

    // ---------------------------------------------------------------- swap

    /** Cost change of exchanging the slots of {@code a} and {@code b}, or {@link #INFEASIBLE}. */
    public long swapDelta(int a, int b) {
        int sa = slotOf[a];
        int sb = slotOf[b];
        if (a == b || sa == sb || problem.isLocked(a) || problem.isLocked(b)
                || !problem.allowed(a, sb) || !problem.allowed(b, sa)) {
            return INFEASIBLE;
        }
        long sizeA = problem.nodeSize(a);
        long sizeB = problem.nodeSize(b);
        if (load[sb] - sizeB + sizeA > problem.slotCapacity(sb) || load[sa] - sizeA + sizeB > problem.slotCapacity(sa)) {
            return INFEASIBLE;
        }
        long delta = sideDelta(a, b, sa, sb);
        if (delta == INFEASIBLE) {
            return INFEASIBLE;
        }
        long other = sideDelta(b, a, sb, sa);
        return other == INFEASIBLE ? INFEASIBLE : delta + other;
    }

    /** Delta of moving {@code v} from {@code from} to {@code to}, ignoring its edge to {@code partner}. */
    private long sideDelta(int v, int partner, int from, int to) {
        int[] nb = graph.neighbours(v);
        int[] w = graph.weights(v);
        int[] h = graph.hardWeights(v);
        long delta = 0;
        for (int i = 0; i < nb.length; i++) {
            if (nb[i] == partner) {
                continue; // symmetric matrices: the a–b edge cost is unchanged by a swap
            }
            int su = slotOf[nb[i]];
            if (su == to) {
                return INFEASIBLE;
            }
            delta += penalties.edgeCost(w[i], h[i], to, su) - penalties.edgeCost(w[i], h[i], from, su);
        }
        return delta;
    }

    public void applySwap(int a, int b, long delta) {
        int sa = slotOf[a];
        int sb = slotOf[b];
        load[sa] += problem.nodeSize(b) - problem.nodeSize(a);
        load[sb] += problem.nodeSize(a) - problem.nodeSize(b);
        slotOf[a] = sb;
        slotOf[b] = sa;
        cost += delta;
    }

    // ---------------------------------------------------------------- Kempe chain

    /**
     * Builds the Kempe chain of {@code v} with respect to its slot and {@code t} and returns
     * the cost change of exchanging the two slots on it, or {@link #INFEASIBLE} when the
     * chain contains a locked node, would leave a domain, or would overflow a slot. The chain
     * stays available for {@link #applyKempe(int, long)} until the next call.
     */
    public long kempeDelta(int v, int t) {
        int s = slotOf[v];
        if (s == t) {
            return INFEASIBLE;
        }
        chainStamp++;
        if (chainStamp == Integer.MAX_VALUE) {
            Arrays.fill(chainMark, 0);
            chainStamp = 1;
        }
        chainSize = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(v);
        chainMark[v] = chainStamp;
        long sizeS = 0;
        long sizeT = 0;
        while (!queue.isEmpty()) {
            int x = queue.poll();
            int sx = slotOf[x];
            int target = sx == s ? t : s;
            if (problem.isLocked(x) || !problem.allowed(x, target)) {
                return INFEASIBLE;
            }
            if (sx == s) {
                sizeS += problem.nodeSize(x);
            } else {
                sizeT += problem.nodeSize(x);
            }
            if (chainSize == chain.length) {
                chain = Arrays.copyOf(chain, chainSize * 2);
            }
            chain[chainSize++] = x;
            for (int u : graph.neighbours(x)) {
                int su = slotOf[u];
                if ((su == s || su == t) && chainMark[u] != chainStamp) {
                    chainMark[u] = chainStamp;
                    queue.add(u);
                }
            }
        }
        if (load[s] - sizeS + sizeT > problem.slotCapacity(s) || load[t] - sizeT + sizeS > problem.slotCapacity(t)) {
            return INFEASIBLE;
        }
        long delta = 0;
        for (int i = 0; i < chainSize; i++) {
            int x = chain[i];
            int from = slotOf[x];
            int to = from == s ? t : s;
            int[] nb = graph.neighbours(x);
            int[] w = graph.weights(x);
            int[] h = graph.hardWeights(x);
            for (int j = 0; j < nb.length; j++) {
                if (chainMark[nb[j]] == chainStamp) {
                    continue; // both endpoints swap s<->t: symmetric cost unchanged
                }
                int su = slotOf[nb[j]];
                delta += penalties.edgeCost(w[j], h[j], to, su) - penalties.edgeCost(w[j], h[j], from, su);
            }
        }
        return delta;
    }

    /** Size of the chain built by the last {@link #kempeDelta} call. */
    public int lastChainSize() {
        return chainSize;
    }

    /** Applies the chain built by the last {@link #kempeDelta(int, int)} call for slot {@code t}. */
    public void applyKempe(int t, long delta) {
        int s = slotOf[chain[0]];
        for (int i = 0; i < chainSize; i++) {
            int x = chain[i];
            int from = slotOf[x];
            int to = from == s ? t : s;
            load[from] -= problem.nodeSize(x);
            load[to] += problem.nodeSize(x);
            slotOf[x] = to;
        }
        cost += delta;
    }

    /** Replaces the whole assignment, e.g. to restore the best solution found. */
    public void restore(int[] slots) {
        System.arraycopy(slots, 0, slotOf, 0, slotOf.length);
        Arrays.fill(load, 0);
        for (int v = 0; v < slotOf.length; v++) {
            load[slotOf[v]] += problem.nodeSize(v);
        }
        cost = fullCost();
    }
}
