package com.examgrid.backend.publishing;

import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.PlanSnapshot.StudentTicket;
import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.scheduling.RunViews.SeatView;
import com.examgrid.backend.scheduling.RunViews.TimetableEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    private final PdfRenderer renderer = new PdfRenderer(new PublishingProperties(2, "Test University"));
    private final LocalDate day = LocalDate.of(2026, 12, 7);

    @Test
    void rendersEveryDocumentKindAsPdf() {
        StudentTicket ticket = new StudentTicket("21CS045", "Asha Rao", "CS", List.of(
                new SeatInfo("CS301", "Algorithms", "2026-12-07-MORNING", day, "MORNING", "R101", "Main", "B4")));
        RoomPlan room = new RoomPlan("2026-12-07-MORNING", "R101", "Main", 2, 3, List.of("A1"), 5,
                List.of(new SeatView(0, 1, "A2", "21CS045", "CS301"), new SeatView(1, 0, "B1", "21CS046", "MA201")),
                List.of("F001"), List.of("CS301", "MA201"));
        assertPdf(renderer.hallTicket("Run 1", ticket));
        assertPdf(renderer.seatingCharts("Run 1", "2026-12-07-MORNING", List.of(room, room)));
        assertPdf(renderer.timetable("Run 1", List.of(new TimetableEntry("CS301", "Algorithms", "CS",
                "2026-12-07-MORNING", day, "MORNING", 60, List.of("R101")))));
        assertPdf(renderer.invigilationRoster("Run 1", List.of(new InvigilationEntry("F001", "Dr Rao",
                "2026-12-07-MORNING", day, "MORNING", "R101"))));
    }

    @Test
    void handlesStudentsWithoutName() {
        assertPdf(renderer.hallTicket("Run 1", new StudentTicket("21CS099", "", "", List.of())));
    }

    private static void assertPdf(byte[] bytes) {
        assertThat(bytes.length).isGreaterThan(500);
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
