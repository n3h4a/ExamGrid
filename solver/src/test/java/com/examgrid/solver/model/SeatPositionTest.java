package com.examgrid.solver.model;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatPositionTest {

    @Test
    void labelsUseSpreadsheetRowLetters() {
        assertThat(new SeatPosition(0, 0).label()).isEqualTo("A1");
        assertThat(new SeatPosition(1, 3).label()).isEqualTo("B4");
        assertThat(new SeatPosition(25, 9).label()).isEqualTo("Z10");
        assertThat(new SeatPosition(26, 0).label()).isEqualTo("AA1");
    }

    @Property
    void parseInvertsLabel(@ForAll @IntRange(max = 800) int row, @ForAll @IntRange(max = 99) int column) {
        SeatPosition p = new SeatPosition(row, column);
        assertThat(SeatPosition.parse(p.label())).isEqualTo(p);
    }

    @Test
    void rejectsMalformedLabels() {
        assertThatThrownBy(() -> SeatPosition.parse("12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeatPosition.parse("B")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeatPosition.parse("B0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roomColourClassesNeverContainNeighbours() {
        Room room = Room.grid("R", 5, 7);
        for (boolean diagonal : new boolean[] {false, true}) {
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 7; c++) {
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            boolean neighbour = (dr != 0 || dc != 0) && (diagonal || dr == 0 || dc == 0);
                            if (neighbour && room.isUsable(r + dr, c + dc)) {
                                assertThat(Room.colourClass(r, c, diagonal))
                                        .isNotEqualTo(Room.colourClass(r + dr, c + dc, diagonal));
                            }
                        }
                    }
                }
            }
        }
        assertThat(room.maxSamePaper(false)).isEqualTo(18);
        assertThat(room.maxSamePaper(true)).isEqualTo(12);
    }
}
