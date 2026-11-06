package com.examgrid.solver.util;

import java.util.Arrays;

/**
 * Maximum bipartite matching with Kuhn's augmenting-path algorithm, O(V·E). Used to test
 * Hall's condition: "can these exams each get their own slot, given which slots each may
 * use?". The graphs involved are tiny (one student's exams, or one clique), so the simple
 * algorithm is the right tool.
 */
public final class BipartiteMatching {

    private final boolean[][] allowed;
    private final int right;
    private final int[] matchOfRight;
    private boolean[] visited;

    /**
     * @param allowed {@code allowed[l][r]} is true when left vertex {@code l} may be matched to
     *                right vertex {@code r}; every row must have the same length
     */
    public BipartiteMatching(boolean[][] allowed) {
        this.allowed = allowed;
        this.right = allowed.length == 0 ? 0 : allowed[0].length;
        this.matchOfRight = new int[right];
    }

    /** Size of a maximum matching. */
    public int maximumMatching() {
        Arrays.fill(matchOfRight, -1);
        int matched = 0;
        for (int l = 0; l < allowed.length; l++) {
            visited = new boolean[right];
            if (augment(l)) {
                matched++;
            }
        }
        return matched;
    }

    private boolean augment(int l) {
        for (int r = 0; r < right; r++) {
            if (allowed[l][r] && !visited[r]) {
                visited[r] = true;
                if (matchOfRight[r] < 0 || augment(matchOfRight[r])) {
                    matchOfRight[r] = l;
                    return true;
                }
            }
        }
        return false;
    }
}
