package com.examgrid.solver.seating;

import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class SeatingSolverTest {

    @Test
    void twoEqualPapersFormACheckerboard() {
        Room room = Room.grid("R", 4, 6);
        SeatingPlan plan = new SeatingSolver(false).solve(room, new int[] {12, 12});
        assertValid(plan, new int[] {12, 12}, false);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 6; c++) {
                assertThat(plan.paperAt(r, c)).isEqualTo(plan.paperAt(0, 0) == 0 ? (r + c) % 2 : 1 - (r + c) % 2);
            }
        }
    }

    @Test
    void threePapersThatMustBeSplitAcrossColourClassesUseTheCsp() {
        // Classes of 24 each: 20 + 20 + 8 cannot be packed whole, 8 must straddle both classes.
        Room room = Room.grid("R", 6, 8);
        int[] counts = {20, 20, 8};
        assertValid(new SeatingSolver(false).solve(room, counts), counts, false);
    }

    @Test
    void diagonalAdjacencyUsesFourClasses() {
        Room room = Room.grid("R", 6, 6);
        int[] counts = {9, 9, 9, 9};
        assertValid(new SeatingSolver(true).solve(room, counts), counts, true);
    }

    @Test
    void respectsBlockedSeats() {
        Room room = new Room("R", "", 3, 3, Set.of(new SeatPosition(1, 1)));
        int[] counts = {4, 4};
        SeatingPlan plan = new SeatingSolver(false).solve(room, counts);
        assertValid(plan, counts, false);
        assertThat(plan.paperAt(1, 1)).isEqualTo(SeatingPlan.EMPTY);
    }

    @Test
    void rejectsAPaperLargerThanHalfTheRoom() {
        assertThat(new SeatingSolver(false).solve(Room.grid("R", 4, 4), new int[] {9})).isNull();
        assertThat(new SeatingSolver(false).solve(Room.grid("R", 4, 4), new int[] {8})).isNotNull();
    }

    @Test
    void rejectsMoreCandidatesThanSeats() {
        assertThat(new SeatingSolver(false).solve(Room.grid("R", 2, 2), new int[] {2, 2, 1})).isNull();
    }

    @Property(tries = 300)
    void anyReturnedPlanIsValid(@ForAll @IntRange(min = 1, max = 9) int rows,
                                @ForAll @IntRange(min = 1, max = 9) int cols,
                                @ForAll @IntRange(min = 1, max = 6) int papers,
                                @ForAll boolean diagonal, @ForAll long seed) {
        Room room = Room.grid("R", rows, cols);
        SplittableRandom rnd = new SplittableRandom(seed);
        int[] counts = new int[papers];
        int budget = room.capacity();
        for (int p = 0; p < papers && budget > 0; p++) {
            counts[p] = rnd.nextInt(Math.min(budget, room.maxSamePaper(diagonal)) + 1);
            budget -= counts[p];
        }
        SeatingPlan plan = new SeatingSolver(diagonal).solve(room, counts);
        if (plan != null) {
            assertValid(plan, counts, diagonal);
        }
    }

    static void assertValid(SeatingPlan plan, int[] counts, boolean diagonal) {
        assertThat(plan).isNotNull();
        Room room = plan.room();
        int[] seen = new int[counts.length];
        for (int r = 0; r < room.rows(); r++) {
            for (int c = 0; c < room.columns(); c++) {
                int p = plan.paperAt(r, c);
                if (p == SeatingPlan.EMPTY) {
                    continue;
                }
                assertThat(room.isUsable(r, c)).isTrue();
                seen[p]++;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        boolean neighbour = (dr != 0 || dc != 0) && (diagonal || dr == 0 || dc == 0);
                        int rr = r + dr;
                        int cc = c + dc;
                        if (neighbour && rr >= 0 && cc >= 0 && rr < room.rows() && cc < room.columns()) {
                            assertThat(plan.paperAt(rr, cc)).as("seat %d,%d next to %d,%d", r, c, rr, cc)
                                    .isNotEqualTo(p);
                        }
                    }
                }
            }
        }
        assertThat(seen).isEqualTo(counts);
    }
}
