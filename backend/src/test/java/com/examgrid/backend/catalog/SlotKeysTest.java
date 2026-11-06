package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SlotKeysTest {

    @Test
    void parsesAllAcceptedForms() {
        LocalDate d = LocalDate.of(2026, 12, 14);
        assertThat(SlotKeys.parse("2026-12-14-AFTERNOON")).contains(new SlotKeys.Key(d, Session.AFTERNOON));
        assertThat(SlotKeys.parse("2026-12-14 morning")).contains(new SlotKeys.Key(d, Session.MORNING));
        assertThat(SlotKeys.parse(" 2026-12-14 ")).contains(new SlotKeys.Key(d, null));
    }

    @Test
    void rejectsGarbage() {
        assertThat(SlotKeys.parse("14/12/2026")).isEmpty();
        assertThat(SlotKeys.parse("2026-12-14-NIGHT")).isEmpty();
        assertThat(SlotKeys.parse(null)).isEmpty();
        assertThat(SlotKeys.parse("2026-02-30")).isEmpty();
    }
}
