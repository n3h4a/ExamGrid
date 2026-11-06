package com.examgrid.backend.publishing;

import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.PlanSnapshot.StudentTicket;
import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.scheduling.RunViews.SeatView;
import com.examgrid.backend.scheduling.RunViews.TimetableEntry;
import com.examgrid.solver.model.SeatPosition;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders the published documents with OpenPDF. Layouts are deliberately plain (they are
 * printed in bulk on office printers): a hall ticket per student, one seating-chart page per
 * room per slot, the timetable and the invigilation roster.
 */
@Component
public class PdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 9.5f, Font.NORMAL);
    private static final Font SMALL = new Font(Font.HELVETICA, 7, Font.NORMAL);
    private static final Font SMALL_BOLD = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final Font HEADER_CELL = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Color BRAND = new Color(0x1F, 0x3A, 0x5F);
    private static final Color STRIPE = new Color(0xF2, 0xF5, 0xF9);
    private static final Color BLOCKED = new Color(0xD9, 0xD9, 0xD9);
    private static final Color[] PAPER_TINTS = {
            new Color(0xDD, 0xEB, 0xF7), new Color(0xFC, 0xE4, 0xD6), new Color(0xE2, 0xEF, 0xDA),
            new Color(0xFF, 0xF2, 0xCC), new Color(0xE4, 0xDF, 0xEC), new Color(0xD9, 0xF2, 0xF2)};

    private final String institution;

    public PdfRenderer(PublishingProperties properties) {
        this.institution = properties.institutionName();
    }

    public byte[] hallTicket(String runLabel, StudentTicket student) {
        return render(PageSize.A4, doc -> {
            header(doc, "Hall ticket", runLabel);
            PdfPTable who = new PdfPTable(new float[] {1, 3});
            who.setWidthPercentage(100);
            labelValue(who, "Roll number", student.roll());
            labelValue(who, "Name", student.name().isBlank() ? "—" : student.name());
            labelValue(who, "Department", student.department().isBlank() ? "—" : student.department());
            labelValue(who, "Exams", String.valueOf(student.exams().size()));
            doc.add(who);
            doc.add(spacer());
            PdfPTable table = table(new float[] {2f, 1.2f, 1.1f, 3.1f, 2.2f, 0.8f},
                    "Date", "Session", "Course", "Title", "Room", "Seat");
            int i = 0;
            for (SeatInfo e : student.exams()) {
                boolean stripe = i++ % 2 == 1;
                cells(table, stripe, e.date().format(DATE), capitalise(e.session()), e.courseCode(), e.courseName(),
                        e.roomCode() + (e.building().isBlank() ? "" : " · " + e.building()), e.seat());
            }
            doc.add(table);
            doc.add(spacer());
            doc.add(new Paragraph("Instructions", HEADING));
            for (String line : List.of(
                    "Carry this hall ticket and your college identity card to every exam.",
                    "Be seated 15 minutes before the start. Seats are allotted and must not be changed.",
                    "Seat labels read row letter then bench number, counted from the front left of the hall.",
                    "Electronic devices are not allowed in the examination hall.")) {
                doc.add(new Paragraph("•  " + line, BODY));
            }
        });
    }

    /** One page per room: the seat grid with roll numbers, tinted by paper. */
    public byte[] seatingCharts(String runLabel, String slot, List<RoomPlan> rooms) {
        return render(PageSize.A4.rotate(), doc -> {
            boolean first = true;
            for (RoomPlan room : rooms) {
                if (!first) {
                    doc.newPage();
                }
                first = false;
                header(doc, "Seating chart · " + slot + " · " + room.roomCode()
                        + (room.building().isBlank() ? "" : " (" + room.building() + ")"), runLabel);
                Map<String, Color> tint = new LinkedHashMap<>();
                for (String c : room.courses()) {
                    tint.put(c, PAPER_TINTS[tint.size() % PAPER_TINTS.length]);
                }
                Paragraph legend = new Paragraph();
                legend.add(new Phrase("Papers: ", HEADING));
                tint.forEach((course, color) -> legend.add(new Phrase(course + "   ", BODY)));
                legend.add(new Phrase("   Invigilators: " + String.join(", ", room.invigilators()), BODY));
                doc.add(legend);
                doc.add(new Paragraph("FRONT OF HALL", SMALL_BOLD));
                Map<String, SeatView> byPosition = new HashMap<>();
                room.seats().forEach(s -> byPosition.put(s.row() + ":" + s.column(), s));
                Set<String> blocked = new HashSet<>(room.blockedSeats());
                PdfPTable grid = new PdfPTable(room.columns());
                grid.setWidthPercentage(100);
                for (int r = 0; r < room.rows(); r++) {
                    for (int c = 0; c < room.columns(); c++) {
                        String label = new SeatPosition(r, c).label();
                        SeatView seat = byPosition.get(r + ":" + c);
                        PdfPCell cell = new PdfPCell();
                        cell.setPadding(3);
                        cell.setMinimumHeight(30);
                        cell.addElement(new Paragraph(label, SMALL_BOLD));
                        if (blocked.contains(label)) {
                            cell.setBackgroundColor(BLOCKED);
                            cell.addElement(new Paragraph("blocked", SMALL));
                        } else if (seat != null) {
                            cell.setBackgroundColor(tint.getOrDefault(seat.courseCode(), Color.WHITE));
                            cell.addElement(new Paragraph(seat.roll(), SMALL));
                            cell.addElement(new Paragraph(seat.courseCode(), SMALL));
                        }
                        grid.addCell(cell);
                    }
                }
                doc.add(grid);
                doc.add(new Paragraph(room.seats().size() + " of " + room.capacity() + " seats occupied", SMALL));
            }
        });
    }

    public byte[] timetable(String runLabel, List<TimetableEntry> entries) {
        return render(PageSize.A4, doc -> {
            header(doc, "Examination timetable", runLabel);
            PdfPTable table = table(new float[] {2.2f, 1.3f, 1.2f, 4f, 1, 1}, "Date", "Session", "Course", "Title",
                    "Dept", "Candidates");
            int i = 0;
            for (TimetableEntry e : entries) {
                cells(table, i++ % 2 == 1, e.date().format(DATE), capitalise(e.session()), e.courseCode(),
                        e.courseName(), e.department(), String.valueOf(e.candidates()));
            }
            doc.add(table);
        });
    }

    public byte[] invigilationRoster(String runLabel, List<InvigilationEntry> entries) {
        return render(PageSize.A4, doc -> {
            header(doc, "Invigilation roster", runLabel);
            PdfPTable table = table(new float[] {2.2f, 1.3f, 1.2f, 1.2f, 3}, "Date", "Session", "Room", "Staff", "Name");
            int i = 0;
            for (InvigilationEntry e : entries) {
                cells(table, i++ % 2 == 1, e.date().format(DATE), capitalise(e.session()), e.roomCode(),
                        e.facultyCode(), e.facultyName());
            }
            doc.add(table);
        });
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private static byte[] render(Rectangle size, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(size, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.addCreator("ExamGrid");
        doc.open();
        try {
            body.write(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("could not render PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void header(Document doc, String title, String runLabel) throws DocumentException {
        Paragraph inst = new Paragraph(institution, HEADING);
        doc.add(inst);
        Paragraph t = new Paragraph(title, TITLE);
        t.setSpacingAfter(2);
        doc.add(t);
        Paragraph sub = new Paragraph(runLabel, SMALL);
        sub.setSpacingAfter(10);
        doc.add(sub);
    }

    private static Paragraph spacer() {
        Paragraph p = new Paragraph(" ", BODY);
        p.setSpacingAfter(4);
        return p;
    }

    private static void labelValue(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, HEADING));
        PdfPCell v = new PdfPCell(new Phrase(value, BODY));
        for (PdfPCell c : new PdfPCell[] {l, v}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(3);
            table.addCell(c);
        }
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_CELL));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            table.addCell(cell);
        }
        return table;
    }

    private static void cells(PdfPTable table, boolean stripe, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v == null ? "" : v, BODY));
            cell.setPadding(4);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            if (stripe) {
                cell.setBackgroundColor(STRIPE);
            }
            table.addCell(cell);
        }
    }

    private static String capitalise(String session) {
        return session.charAt(0) + session.substring(1).toLowerCase(Locale.ROOT);
    }
}
