package com.examgrid.backend.scheduling;

import com.examgrid.backend.scheduling.RunViews.Approval;
import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.scheduling.RunViews.SeatView;
import com.examgrid.backend.scheduling.RunViews.StudentExam;
import com.examgrid.backend.scheduling.RunViews.StudentPlan;
import com.examgrid.backend.scheduling.RunViews.TimetableEntry;
import com.examgrid.backend.catalog.SlotKeys;
import com.examgrid.backend.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read model of a stored plan, queried straight from the assignment tables. */
@Component
@Transactional(readOnly = true)
public class PlanQueries {

    private static final String SLOT_KEY = "to_char(sl.slot_date, 'YYYY-MM-DD') || '-' || sl.session";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PlanQueries(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<TimetableEntry> timetable(long runId) {
        Map<String, List<String>> rooms = new LinkedHashMap<>();
        jdbc.query("select distinct c.code, r.code from seat_assignment sa join course c on c.id = sa.course_id "
                + "join room r on r.id = sa.room_id where sa.run_id = ? order by 1, 2", rs -> {
            rooms.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, runId);
        return jdbc.query("select c.code, c.name, c.department, " + SLOT_KEY + ", sl.slot_date, sl.session, "
                        + "(select count(*) from seat_assignment sa where sa.run_id = ea.run_id and sa.course_id = c.id) "
                        + "from exam_assignment ea join course c on c.id = ea.course_id "
                        + "join exam_slot sl on sl.id = ea.slot_id where ea.run_id = ? "
                        + "order by sl.slot_date, sl.session, c.code",
                (rs, i) -> new TimetableEntry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getDate(5).toLocalDate(), rs.getString(6), rs.getInt(7),
                        rooms.getOrDefault(rs.getString(1), List.of())), runId);
    }

    /** Rooms in use in a slot, with every occupied seat, for the seating-chart view. */
    public List<RoomPlan> rooms(long runId, String slotKey) {
        long slotId = slotId(slotKey);
        Map<Long, List<SeatView>> seats = new LinkedHashMap<>();
        jdbc.query("select sa.room_id, sa.seat_row, sa.seat_col, sa.seat_label, s.roll, c.code from seat_assignment sa "
                + "join student s on s.id = sa.student_id join course c on c.id = sa.course_id "
                + "where sa.run_id = ? and sa.slot_id = ? order by sa.room_id, sa.seat_row, sa.seat_col", rs -> {
            seats.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new SeatView(rs.getInt(2), rs.getInt(3),
                    rs.getString(4), rs.getString(5), rs.getString(6)));
        }, runId, slotId);
        Map<Long, List<String>> invigilators = new LinkedHashMap<>();
        jdbc.query("select i.room_id, f.code from invigilation i join faculty f on f.id = i.faculty_id "
                + "where i.run_id = ? and i.slot_id = ? order by f.code", rs -> {
            invigilators.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, runId, slotId);
        List<RoomPlan> out = new ArrayList<>();
        for (Map.Entry<Long, List<SeatView>> e : seats.entrySet()) {
            jdbc.query("select code, building, seat_rows, seat_columns, blocked_seats::text, capacity from room "
                    + "where id = ?", rs -> {
                out.add(new RoomPlan(slotKey, rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        mapper.readValue(rs.getString(5), new TypeReference<List<String>>() {
                        }), rs.getInt(6), e.getValue(), invigilators.getOrDefault(e.getKey(), List.of()),
                        e.getValue().stream().map(SeatView::courseCode).distinct().sorted().toList()));
            }, e.getKey());
        }
        out.sort((a, b) -> a.roomCode().compareTo(b.roomCode()));
        return out;
    }

    public List<InvigilationEntry> invigilation(long runId, String facultyCode) {
        String filter = facultyCode == null ? "" : " and f.code = ?";
        Object[] args = facultyCode == null ? new Object[] {runId} : new Object[] {runId, facultyCode};
        return jdbc.query("select f.code, f.name, " + SLOT_KEY + ", sl.slot_date, sl.session, r.code "
                        + "from invigilation i join faculty f on f.id = i.faculty_id join exam_slot sl on sl.id = i.slot_id "
                        + "join room r on r.id = i.room_id where i.run_id = ?" + filter
                        + " order by sl.slot_date, sl.session, r.code, f.code",
                (rs, i) -> new InvigilationEntry(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDate(4).toLocalDate(), rs.getString(5), rs.getString(6)), args);
    }

    public Optional<StudentPlan> student(long runId, String roll) {
        List<String> name = jdbc.queryForList("select name from student where roll = ?", String.class, roll);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        List<StudentExam> exams = jdbc.query("select c.code, c.name, " + SLOT_KEY + ", sl.slot_date, sl.session, "
                        + "r.code, r.building, sa.seat_label from seat_assignment sa "
                        + "join student s on s.id = sa.student_id join course c on c.id = sa.course_id "
                        + "join exam_slot sl on sl.id = sa.slot_id join room r on r.id = sa.room_id "
                        + "where sa.run_id = ? and s.roll = ? order by sl.slot_date, sl.session",
                (rs, i) -> new StudentExam(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDate(4).toLocalDate(), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8)), runId, roll);
        return Optional.of(new StudentPlan(roll, name.getFirst(), exams));
    }

    public List<Approval> approvals(long runId) {
        return jdbc.query("select u.username, u.display_name, a.approved_at, a.comment from approval a "
                        + "join app_user u on u.id = a.user_id where a.run_id = ? order by a.approved_at",
                (rs, i) -> new Approval(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getString(4)), runId);
    }

    private long slotId(String key) {
        SlotKeys.Key k = SlotKeys.parse(key).filter(x -> x.session() != null)
                .orElseThrow(() -> ApiException.badRequest("invalid_slot", "'" + key
                        + "' is not a slot like 2026-12-01-MORNING."));
        List<Long> ids = jdbc.queryForList("select id from exam_slot where slot_date = ? and session = ?", Long.class,
                java.sql.Date.valueOf(k.date()), k.session().name());
        if (ids.isEmpty()) {
            throw ApiException.notFound("Slot", key);
        }
        return ids.getFirst();
    }
}
