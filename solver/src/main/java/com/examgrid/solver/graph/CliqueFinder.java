package com.examgrid.solver.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Greedy maximum-clique heuristic. A clique of q mutually conflicting exams needs q distinct
 * slots, so any clique larger than the number of slots <em>proves</em> infeasibility, which
 * makes it the most convincing thing to show an exam cell.
 *
 * <p>From every seed node (highest degree first) the clique grows by the candidate with the
 * most neighbours among the remaining candidates. O(n · d²) for maximum degree d.
 */
public final class CliqueFinder {

    private CliqueFinder() {
    }

    public static int[] largestClique(ConflictGraph g) {
        int n = g.nodeCount();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(g.degree(b), g.degree(a)));
        int[] best = n == 0 ? new int[0] : new int[] {order[0]};
        for (int seed : order) {
            if (g.degree(seed) + 1 <= best.length) {
                break;
            }
            List<Integer> clique = new ArrayList<>();
            clique.add(seed);
            List<Integer> candidates = new ArrayList<>();
            for (int u : g.neighbours(seed)) {
                candidates.add(u);
            }
            while (!candidates.isEmpty()) {
                int pick = -1;
                int pickScore = -1;
                for (int c : candidates) {
                    int score = 0;
                    for (int other : candidates) {
                        if (other != c && g.adjacent(c, other)) {
                            score++;
                        }
                    }
                    if (score > pickScore) {
                        pickScore = score;
                        pick = c;
                    }
                }
                clique.add(pick);
                List<Integer> next = new ArrayList<>();
                for (int c : candidates) {
                    if (c != pick && g.adjacent(pick, c)) {
                        next.add(c);
                    }
                }
                candidates = next;
            }
            if (clique.size() > best.length) {
                best = clique.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        Arrays.sort(best);
        return best;
    }
}
