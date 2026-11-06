package com.examgrid.solver.optimize;

import com.examgrid.solver.model.PenaltyWeights;
import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PenaltyModelTest {

    // Mon AM, Mon PM, Tue AM, Tue PM, Thu AM (Wednesday is a gap day)
    private final List<Slot> slots = List.of(
            Slot.of(LocalDate.of(2026, 12, 7), Session.MORNING),
            Slot.of(LocalDate.of(2026, 12, 7), Session.AFTERNOON),
            Slot.of(LocalDate.of(2026, 12, 8), Session.MORNING),
            Slot.of(LocalDate.of(2026, 12, 8), Session.AFTERNOON),
            Slot.of(LocalDate.of(2026, 12, 10), Session.MORNING));
    private final PenaltyModel model = new PenaltyModel(slots, PenaltyWeights.DEFAULT);

    @Test
    void sameDayPairsCostTheSameDayWeight() {
        assertThat(model.pair(0, 1)).isEqualTo(10);
        assertThat(model.pair(2, 3)).isEqualTo(10);
    }

    @Test
    void overnightConsecutiveSlotsAreBackToBack() {
        assertThat(model.backToBack(1, 2)).isTrue();
        assertThat(model.pair(1, 2)).isEqualTo(3);
        // Morning to next morning is not consecutive in slot order.
        assertThat(model.backToBack(0, 2)).isFalse();
        // Tuesday afternoon to Thursday morning is consecutive in order but not on consecutive days.
        assertThat(model.backToBack(3, 4)).isFalse();
        assertThat(model.pair(3, 4)).isZero();
    }

    @Test
    void hardGapAppliesBelowTheMinimumNumberOfDays() {
        assertThat(model.hard(0, 1)).isEqualTo(5);
        assertThat(model.hard(0, 2)).isEqualTo(5);
        assertThat(model.hard(2, 4)).isZero();
        assertThat(model.hard(0, 0)).isZero();
    }

    @Test
    void matricesAreSymmetric() {
        for (int a = 0; a < slots.size(); a++) {
            for (int b = 0; b < slots.size(); b++) {
                assertThat(model.pair(a, b)).isEqualTo(model.pair(b, a));
                assertThat(model.hard(a, b)).isEqualTo(model.hard(b, a));
            }
        }
    }

    @Test
    void edgeCostCombinesBothTerms() {
        assertThat(model.edgeCost(4, 2, 0, 1)).isEqualTo(4 * 10 + 2 * 5);
    }
}
