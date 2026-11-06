package com.examgrid.solver.rooms;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Min-cost max-flow by successive shortest paths, using SPFA (queue-based Bellman–Ford) so
 * negative residual costs are handled. The networks built for room allocation have a few
 * hundred nodes, for which this is both simple and fast.
 */
public final class MinCostMaxFlow {

    private final int n;
    private int[] head;
    private int[] next = new int[64];
    private int[] to = new int[64];
    private int[] cap = new int[64];
    private long[] cost = new long[64];
    private int edges;

    public MinCostMaxFlow(int nodes) {
        this.n = nodes;
        this.head = new int[nodes];
        Arrays.fill(head, -1);
    }

    /** Adds a directed edge and returns its id; the reverse residual edge is {@code id ^ 1}. */
    public int addEdge(int from, int target, int capacity, long unitCost) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        int id = edges;
        push(from, target, capacity, unitCost);
        push(target, from, 0, -unitCost);
        return id;
    }

    private void push(int from, int target, int capacity, long unitCost) {
        if (edges == to.length) {
            int size = edges * 2;
            next = Arrays.copyOf(next, size);
            to = Arrays.copyOf(to, size);
            cap = Arrays.copyOf(cap, size);
            cost = Arrays.copyOf(cost, size);
        }
        to[edges] = target;
        cap[edges] = capacity;
        cost[edges] = unitCost;
        next[edges] = head[from];
        head[from] = edges;
        edges++;
    }

    /** Flow currently on edge {@code id} (as returned by {@link #addEdge}). */
    public int flow(int id) {
        return cap[id ^ 1];
    }

    /** Pushes as much flow as possible from {@code source} to {@code sink} at minimum cost. */
    public Result solve(int source, int sink) {
        long[] dist = new long[n];
        int[] viaEdge = new int[n];
        boolean[] queued = new boolean[n];
        int flow = 0;
        long total = 0;
        while (true) {
            Arrays.fill(dist, Long.MAX_VALUE);
            Arrays.fill(viaEdge, -1);
            dist[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            queued[source] = true;
            while (!queue.isEmpty()) {
                int u = queue.poll();
                queued[u] = false;
                for (int e = head[u]; e >= 0; e = next[e]) {
                    if (cap[e] > 0 && dist[u] + cost[e] < dist[to[e]]) {
                        dist[to[e]] = dist[u] + cost[e];
                        viaEdge[to[e]] = e;
                        if (!queued[to[e]]) {
                            queued[to[e]] = true;
                            queue.add(to[e]);
                        }
                    }
                }
            }
            if (dist[sink] == Long.MAX_VALUE) {
                return new Result(flow, total);
            }
            int push = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = to[viaEdge[v] ^ 1]) {
                push = Math.min(push, cap[viaEdge[v]]);
            }
            for (int v = sink; v != source; v = to[viaEdge[v] ^ 1]) {
                cap[viaEdge[v]] -= push;
                cap[viaEdge[v] ^ 1] += push;
            }
            flow += push;
            total += push * dist[sink];
        }
    }

    public record Result(int flow, long cost) {
    }
}
