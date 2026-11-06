package com.examgrid.solver.rooms;

import com.examgrid.solver.model.Room;
import com.examgrid.solver.seating.SeatingPlan;
import com.examgrid.solver.seating.SeatingSolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides how many candidates of each paper sit in each room for one slot, then seats them.
 *
 * <p>The counts come from a min-cost max-flow network:
 * <pre>
 * source ─(candidates of paper)→ paper ─(mixing cap)→ room ─(seats, cost = room rank)→ sink
 * </pre>
 * If the max flow equals the number of candidates, everyone fits. The <em>cost</em> on
 * room → sink edges fills rooms in priority order, so the plan opens as few low-priority rooms
 * as it can. The <em>mixing cap</em> on paper → room edges is the largest number of one
 * paper's candidates the room can hold without two of them being neighbours. That stops a
 * single paper from dominating a room, which is what makes the seating step (a CSP on the
 * seat grid) solvable.
 *
 * <p>The cap is necessary but not sufficient when several papers share a room. If the
 * seating solver rejects a room, the cap for that room's largest paper is lowered to its
 * current count minus one and the flow is solved again. Each round strictly tightens one cap,
 * so the loop terminates.
 */
public final class RoomAllocator {

    private static final int MAX_ROUNDS = 200;

    private final SeatingSolver seating;
    private final boolean diagonal;

    public RoomAllocator(boolean diagonal) {
        this.diagonal = diagonal;
        this.seating = new SeatingSolver(diagonal);
    }

    /** Allocation of one slot. {@code counts[p][r]} candidates of paper p sit in room r. */
    public record Allocation(int[][] counts, SeatingPlan[] plans) {

        /** Rooms with at least one candidate. */
        public int roomsUsed() {
            int used = 0;
            for (SeatingPlan plan : plans) {
                used += plan == null ? 0 : 1;
            }
            return used;
        }
    }

    /**
     * @param paperSizes candidates per paper in this slot
     * @param rooms      rooms available in this slot, in priority order
     * @return the allocation, or {@code null} when the candidates cannot be seated
     */
    public Allocation allocate(int[] paperSizes, List<Room> rooms) {
        int papers = paperSizes.length;
        int roomCount = rooms.size();
        int[][] cap = new int[papers][roomCount];
        for (int p = 0; p < papers; p++) {
            for (int r = 0; r < roomCount; r++) {
                cap[p][r] = Math.min(paperSizes[p], rooms.get(r).maxSamePaper(diagonal));
            }
        }
        int total = 0;
        for (int size : paperSizes) {
            total += size;
        }
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int[][] counts = flow(paperSizes, rooms, cap, total);
            if (counts == null) {
                return null;
            }
            SeatingPlan[] plans = new SeatingPlan[roomCount];
            boolean retry = false;
            for (int r = 0; r < roomCount; r++) {
                int[] inRoom = new int[papers];
                int sum = 0;
                for (int p = 0; p < papers; p++) {
                    inRoom[p] = counts[p][r];
                    sum += inRoom[p];
                }
                if (sum == 0) {
                    continue;
                }
                plans[r] = seating.solve(rooms.get(r), inRoom);
                if (plans[r] == null) {
                    int largest = 0;
                    for (int p = 1; p < papers; p++) {
                        if (inRoom[p] > inRoom[largest]) {
                            largest = p;
                        }
                    }
                    cap[largest][r] = inRoom[largest] - 1;
                    retry = true;
                }
            }
            if (!retry) {
                return new Allocation(counts, plans);
            }
        }
        return null;
    }

    private static int[][] flow(int[] sizes, List<Room> rooms, int[][] cap, int total) {
        int papers = sizes.length;
        int roomCount = rooms.size();
        int source = 0;
        int sink = 1 + papers + roomCount;
        MinCostMaxFlow mcmf = new MinCostMaxFlow(sink + 1);
        for (int p = 0; p < papers; p++) {
            mcmf.addEdge(source, 1 + p, sizes[p], 0);
        }
        int[][] edge = new int[papers][roomCount];
        for (int p = 0; p < papers; p++) {
            for (int r = 0; r < roomCount; r++) {
                edge[p][r] = mcmf.addEdge(1 + p, 1 + papers + r, Math.max(0, cap[p][r]), 0);
            }
        }
        for (int r = 0; r < roomCount; r++) {
            mcmf.addEdge(1 + papers + r, sink, rooms.get(r).capacity(), r + 1L);
        }
        if (mcmf.solve(source, sink).flow() < total) {
            return null;
        }
        int[][] counts = new int[papers][roomCount];
        for (int p = 0; p < papers; p++) {
            for (int r = 0; r < roomCount; r++) {
                counts[p][r] = mcmf.flow(edge[p][r]);
            }
        }
        return counts;
    }
}
