package com.examgrid.solver.rooms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinCostMaxFlowTest {

    @Test
    void findsMaximumFlowAtMinimumCost() {
        // 0 → 1 → 3 (cheap, cap 2), 0 → 2 → 3 (expensive, cap 3), 1 → 2 link.
        MinCostMaxFlow g = new MinCostMaxFlow(4);
        int a = g.addEdge(0, 1, 4, 1);
        int b = g.addEdge(0, 2, 3, 5);
        g.addEdge(1, 3, 2, 1);
        g.addEdge(2, 3, 5, 1);
        int link = g.addEdge(1, 2, 2, 1);
        MinCostMaxFlow.Result r = g.solve(0, 3);
        assertThat(r.flow()).isEqualTo(7);
        // 2 units 0-1-3 (cost 2 each), 2 units 0-1-2-3 (cost 3 each), 3 units 0-2-3 (cost 6 each).
        assertThat(r.cost()).isEqualTo(2 * 2 + 2 * 3 + 3 * 6);
        assertThat(g.flow(a)).isEqualTo(4);
        assertThat(g.flow(b)).isEqualTo(3);
        assertThat(g.flow(link)).isEqualTo(2);
    }

    @Test
    void cancelsFlowAlongNegativeResidualEdges() {
        // Greedy first path 0-1-2-3 must be partly undone to reach max flow 2.
        MinCostMaxFlow g = new MinCostMaxFlow(4);
        g.addEdge(0, 1, 1, 0);
        g.addEdge(0, 2, 1, 0);
        g.addEdge(1, 2, 1, 0);
        g.addEdge(1, 3, 1, 10);
        g.addEdge(2, 3, 1, 0);
        MinCostMaxFlow.Result r = g.solve(0, 3);
        assertThat(r.flow()).isEqualTo(2);
        assertThat(r.cost()).isEqualTo(10);
    }

    @Test
    void zeroFlowWhenDisconnected() {
        MinCostMaxFlow g = new MinCostMaxFlow(3);
        g.addEdge(0, 1, 5, 1);
        assertThat(g.solve(0, 2).flow()).isZero();
    }
}
