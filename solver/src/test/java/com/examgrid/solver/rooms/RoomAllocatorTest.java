package com.examgrid.solver.rooms;

import com.examgrid.solver.model.Room;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomAllocatorTest {

    private final RoomAllocator allocator = new RoomAllocator(false);

    @Test
    void fillsRoomsInPriorityOrderWhileMixingPapers() {
        List<Room> rooms = List.of(Room.grid("A", 6, 8), Room.grid("B", 6, 8), Room.grid("C", 6, 8));
        RoomAllocator.Allocation a = allocator.allocate(new int[] {30, 30}, rooms);
        assertThat(a).isNotNull();
        // 60 candidates: first two rooms hold 48 + 12; the third stays closed.
        assertThat(a.counts()[0][0] + a.counts()[1][0]).isEqualTo(48);
        assertThat(a.counts()[0][2] + a.counts()[1][2]).isZero();
        assertThat(a.roomsUsed()).isEqualTo(2);
        for (int r = 0; r < 3; r++) {
            assertThat(a.counts()[0][r]).isLessThanOrEqualTo(24);
            assertThat(a.counts()[1][r]).isLessThanOrEqualTo(24);
        }
    }

    @Test
    void singleLargePaperIsSpreadAtHalfCapacity() {
        List<Room> rooms = List.of(Room.grid("A", 4, 4), Room.grid("B", 4, 4), Room.grid("C", 4, 4));
        RoomAllocator.Allocation a = allocator.allocate(new int[] {20}, rooms);
        assertThat(a).isNotNull();
        assertThat(a.counts()[0]).containsExactly(8, 8, 4);
    }

    @Test
    void returnsNullWhenMixingMakesItImpossible() {
        // 32 seats in total, but a single paper can use only 16 of them.
        assertThat(allocator.allocate(new int[] {20}, List.of(Room.grid("A", 4, 4), Room.grid("B", 4, 4)))).isNull();
    }

    @Test
    void tightensCapsUntilEveryRoomCanBeSeated() {
        List<Room> rooms = List.of(Room.grid("A", 6, 8), Room.grid("B", 6, 8));
        int[] sizes = {20, 20, 20, 20};
        RoomAllocator.Allocation a = allocator.allocate(sizes, rooms);
        assertThat(a).isNotNull();
        for (int r = 0; r < rooms.size(); r++) {
            int[] inRoom = new int[sizes.length];
            for (int p = 0; p < sizes.length; p++) {
                inRoom[p] = a.counts()[p][r];
            }
            if (a.plans()[r] != null) {
                com.examgrid.solver.seating.SeatingSolverTestAccess.assertValid(a.plans()[r], inRoom, false);
            }
        }
    }
}
