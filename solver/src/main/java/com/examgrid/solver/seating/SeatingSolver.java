package com.examgrid.solver.seating;

import com.examgrid.solver.model.Room;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Seats several papers in one room so that no two neighbouring seats hold the same paper
 * (neighbours: left/right/front/back, plus diagonals when {@code diagonal} is set).
 *
 * <p><b>Step 1, colour-class packing.</b> The seat grid is partitioned into independent
 * colour classes: two (a checkerboard) for edge adjacency, four ({@code (row%2, col%2)})
 * with diagonals. Seats inside one class are never adjacent, so if every paper fits
 * <em>entirely</em> inside one class the seating is valid by construction. Finding such an
 * assignment is a small bin-packing problem (papers into 2 or 4 bins) solved exactly by
 * backtracking over papers, largest first. With two papers this is the checkerboard.
 *
 * <p><b>Step 2, seat-level CSP.</b> If no whole-paper packing exists (a paper has to be split
 * across classes), seats are assigned in row-major order by backtracking with forward
 * checking. After each assignment the search checks that every paper still has at least as
 * many unassigned seats free of a same-paper neighbour as it has candidates left.
 *
 * <p>Both steps are budgeted. {@code null} means "not seatable within budget", and the room
 * allocator then moves some candidates to another room.
 */
public final class SeatingSolver {

    private static final int PACKING_BUDGET = 100_000;
    private static final int CSP_BUDGET = 200_000;

    private final boolean diagonal;

    public SeatingSolver(boolean diagonal) {
        this.diagonal = diagonal;
    }

    /**
     * @param counts candidates per paper (local paper indexes)
     * @return a plan, or {@code null} when the counts cannot be seated in this room
     */
    public SeatingPlan solve(Room room, int[] counts) {
        int total = Arrays.stream(counts).sum();
        if (total > room.capacity()) {
            return null;
        }
        int maxSame = room.maxSamePaper(diagonal);
        for (int c : counts) {
            if (c > maxSame) {
                return null;
            }
        }
        SeatingPlan packed = packIntoClasses(room, counts);
        return packed != null ? packed : backtrack(room, counts);
    }

    // ------------------------------------------------------------------ step 1

    private SeatingPlan packIntoClasses(Room room, int[] counts) {
        int[] classSize = room.colourClassSizes(diagonal);
        Integer[] order = papersByCountDescending(counts);
        int[] classOf = new int[counts.length];
        int[] remaining = classSize.clone();
        int[] budget = {PACKING_BUDGET};
        if (!pack(order, 0, counts, remaining, classOf, budget)) {
            return null;
        }
        int[][] grid = emptyGrid(room);
        int[] next = new int[classSize.length];
        // Within each class, fill papers in descending size order along row-major cells.
        List<List<int[]>> cells = new ArrayList<>();
        for (int k = 0; k < classSize.length; k++) {
            cells.add(new ArrayList<>());
        }
        for (int r = 0; r < room.rows(); r++) {
            for (int c = 0; c < room.columns(); c++) {
                if (room.isUsable(r, c)) {
                    cells.get(Room.colourClass(r, c, diagonal)).add(new int[] {r, c});
                }
            }
        }
        for (int p : order) {
            int k = classOf[p];
            for (int i = 0; i < counts[p]; i++) {
                int[] cell = cells.get(k).get(next[k]++);
                grid[cell[0]][cell[1]] = p;
            }
        }
        return new SeatingPlan(room, grid, counts.length);
    }

    private static boolean pack(Integer[] order, int i, int[] counts, int[] remaining, int[] classOf, int[] budget) {
        if (i == order.length) {
            return true;
        }
        if (--budget[0] < 0) {
            return false;
        }
        int p = order[i];
        if (counts[p] == 0) {
            classOf[p] = 0;
            return pack(order, i + 1, counts, remaining, classOf, budget);
        }
        for (int k = 0; k < remaining.length; k++) {
            if (remaining[k] < counts[p] || seenCapacity(remaining, k)) {
                continue;
            }
            remaining[k] -= counts[p];
            classOf[p] = k;
            if (pack(order, i + 1, counts, remaining, classOf, budget)) {
                return true;
            }
            remaining[k] += counts[p];
        }
        return false;
    }

    /** Symmetry breaking: classes with equal remaining capacity are interchangeable. */
    private static boolean seenCapacity(int[] remaining, int k) {
        for (int j = 0; j < k; j++) {
            if (remaining[j] == remaining[k]) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ step 2

    private SeatingPlan backtrack(Room room, int[] counts) {
        List<int[]> seats = new ArrayList<>();
        for (int r = 0; r < room.rows(); r++) {
            for (int c = 0; c < room.columns(); c++) {
                if (room.isUsable(r, c)) {
                    seats.add(new int[] {r, c});
                }
            }
        }
        int n = seats.size();
        int[][] index = new int[room.rows()][room.columns()];
        for (int[] row : index) {
            Arrays.fill(row, -1);
        }
        for (int i = 0; i < n; i++) {
            index[seats.get(i)[0]][seats.get(i)[1]] = i;
        }
        int[][] neighbours = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> nb = new ArrayList<>();
            int r = seats.get(i)[0];
            int c = seats.get(i)[1];
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if ((dr == 0 && dc == 0) || (!diagonal && dr != 0 && dc != 0)) {
                        continue;
                    }
                    int rr = r + dr;
                    int cc = c + dc;
                    if (rr >= 0 && rr < room.rows() && cc >= 0 && cc < room.columns() && index[rr][cc] >= 0) {
                        nb.add(index[rr][cc]);
                    }
                }
            }
            neighbours[i] = nb.stream().mapToInt(Integer::intValue).toArray();
        }
        Csp csp = new Csp(n, neighbours, counts.clone(), n - Arrays.stream(counts).sum());
        if (!csp.search(0)) {
            return null;
        }
        int[][] grid = emptyGrid(room);
        for (int i = 0; i < n; i++) {
            grid[seats.get(i)[0]][seats.get(i)[1]] = csp.assigned[i];
        }
        return new SeatingPlan(room, grid, counts.length);
    }

    private static final class Csp {
        final int n;
        final int[][] neighbours;
        final int[] remaining;
        int empties;
        final int[] assigned;
        final int[][] forbidden;
        int budget = CSP_BUDGET;

        Csp(int n, int[][] neighbours, int[] remaining, int empties) {
            this.n = n;
            this.neighbours = neighbours;
            this.remaining = remaining;
            this.empties = empties;
            this.assigned = new int[n];
            this.forbidden = new int[n][remaining.length];
        }

        boolean search(int i) {
            if (i == n) {
                return true;
            }
            if (--budget < 0) {
                return false;
            }
            Integer[] order = papersByCountDescending(remaining);
            for (int p : order) {
                if (remaining[p] == 0 || forbidden[i][p] > 0) {
                    continue;
                }
                assign(i, p, +1);
                remaining[p]--;
                if (feasibleAhead(i) && search(i + 1)) {
                    return true;
                }
                remaining[p]++;
                assign(i, p, -1);
            }
            if (empties > 0) {
                empties--;
                assigned[i] = SeatingPlan.EMPTY;
                if (feasibleAhead(i) && search(i + 1)) {
                    return true;
                }
                empties++;
            }
            return false;
        }

        private void assign(int i, int p, int sign) {
            assigned[i] = sign > 0 ? p : SeatingPlan.EMPTY;
            for (int j : neighbours[i]) {
                if (j > i) {
                    forbidden[j][p] += sign;
                }
            }
        }

        /** Forward check: every paper still has enough unassigned, non-forbidden seats. */
        private boolean feasibleAhead(int i) {
            for (int p = 0; p < remaining.length; p++) {
                if (remaining[p] == 0) {
                    continue;
                }
                int open = 0;
                for (int j = i + 1; j < n && open < remaining[p]; j++) {
                    if (forbidden[j][p] == 0) {
                        open++;
                    }
                }
                if (open < remaining[p]) {
                    return false;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Integer[] papersByCountDescending(int[] counts) {
        Integer[] order = new Integer[counts.length];
        for (int i = 0; i < counts.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.<Integer>comparingInt(p -> -counts[p]).thenComparingInt(p -> p));
        return order;
    }

    private static int[][] emptyGrid(Room room) {
        int[][] grid = new int[room.rows()][room.columns()];
        for (int[] row : grid) {
            Arrays.fill(row, SeatingPlan.EMPTY);
        }
        return grid;
    }
}
