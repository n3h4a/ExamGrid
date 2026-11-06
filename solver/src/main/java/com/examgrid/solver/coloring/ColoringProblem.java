package com.examgrid.solver.coloring;

import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Slot;

import java.util.Arrays;
import java.util.Map;

/**
 * Graph colouring with side constraints: a conflict graph, K slots (colours), the slots each
 * node may use (session rules and locks), and the seat capacity of each slot.
 */
public final class ColoringProblem {

    private final ConflictGraph graph;
    private final int slotCount;
    private final boolean[][] allowed;
    private final int[] domainSize;
    private final int[] locked;
    private final long[] slotCapacity;

    public ColoringProblem(ConflictGraph graph, int slotCount, boolean[][] allowed, int[] locked,
                           long[] slotCapacity) {
        if (allowed.length != graph.nodeCount() || locked.length != graph.nodeCount()) {
            throw new IllegalArgumentException("domain arrays must have one entry per node");
        }
        if (slotCapacity.length != slotCount) {
            throw new IllegalArgumentException("slotCapacity must have one entry per slot");
        }
        this.graph = graph;
        this.slotCount = slotCount;
        this.allowed = allowed;
        this.locked = locked;
        this.slotCapacity = slotCapacity;
        this.domainSize = new int[allowed.length];
        for (int v = 0; v < allowed.length; v++) {
            for (int s = 0; s < slotCount; s++) {
                if (allowed[v][s]) {
                    domainSize[v]++;
                }
            }
        }
    }

    /** Plain K-colouring: every node may use every slot and capacity is unlimited. */
    public static ColoringProblem unconstrained(ConflictGraph graph, int slotCount) {
        int n = graph.nodeCount();
        boolean[][] allowed = new boolean[n][slotCount];
        int[] locked = new int[n];
        for (int v = 0; v < n; v++) {
            Arrays.fill(allowed[v], true);
            locked[v] = -1;
        }
        long[] cap = new long[slotCount];
        Arrays.fill(cap, Long.MAX_VALUE / 4);
        return new ColoringProblem(graph, slotCount, allowed, locked, cap);
    }

    /**
     * Derives domains from course session rules and locks. A node is locked when any of its
     * courses is; conflicting locks inside one same-slot group leave the node with an empty
     * domain, which the infeasibility analyser reports.
     */
    public static ColoringProblem from(ProblemInstance instance, ConflictGraph graph) {
        int n = graph.nodeCount();
        int k = instance.slotCount();
        boolean[][] allowed = new boolean[n][k];
        int[] locked = new int[n];
        Arrays.fill(locked, -1);
        Map<Integer, Integer> locks = instance.lockedSlots();
        for (int v = 0; v < n; v++) {
            int[] courses = graph.groups().coursesOf(v);
            for (int s = 0; s < k; s++) {
                Slot slot = instance.slot(s);
                boolean ok = true;
                for (int c : courses) {
                    Course course = instance.course(c);
                    Integer lock = locks.get(c);
                    if (!course.allows(slot.session()) || (lock != null && lock != s)) {
                        ok = false;
                        break;
                    }
                }
                allowed[v][s] = ok;
            }
            for (int c : courses) {
                Integer lock = locks.get(c);
                if (lock != null) {
                    locked[v] = lock;
                }
            }
        }
        long seats = instance.roomCount() == 0 ? Long.MAX_VALUE / 4 : instance.totalSeats();
        long[] cap = new long[k];
        Arrays.fill(cap, seats);
        return new ColoringProblem(graph, k, allowed, locked, cap);
    }

    public ConflictGraph graph() { return graph; }
    public int nodeCount() { return graph.nodeCount(); }
    public int slotCount() { return slotCount; }
    public boolean allowed(int node, int slot) { return allowed[node][slot]; }
    public int domainSize(int node) { return domainSize[node]; }
    public boolean isLocked(int node) { return locked[node] >= 0; }
    public int lockedSlot(int node) { return locked[node]; }
    public long slotCapacity(int slot) { return slotCapacity[slot]; }
    public int nodeSize(int node) { return graph.nodeSize(node); }
}
