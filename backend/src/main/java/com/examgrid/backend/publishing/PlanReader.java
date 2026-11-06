package com.examgrid.backend.publishing;

import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.PlanSnapshot.StudentTicket;
import com.examgrid.backend.scheduling.PlanQueries;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bulk reads of a stored plan for publishing and for the seat-lookup fallback. */
@Component
public class PlanReader {

    private static final String SEAT_SELECT = "select s.roll, s.name, s.department, c.code, c.name, "
            + "to_char(sl.slot_date, 'YYYY-MM-DD') || '-' || sl.session, sl.slot_date, sl.session, r.code, r.building, "
            + "sa.seat_label from seat_assignment sa join student s on s.id = sa.student_id "
            + "join course c on c.id = sa.course_id join exam_slot sl on sl.id = sa.slot_id "
            + "join room r on r.id = sa.room_id ";

    private final JdbcTemplate jdbc;
    private final PlanQueries queries;

    public PlanReader(JdbcTemplate jdbc, PlanQueries queries) {
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public PlanSnapshot load(long runId) {
        String label = jdbc.queryForObject("select coalesce(label, 'Run ' || id) from schedule_run where id = ?",
                String.class, runId);
        Map<String, StudentTicket> students = new LinkedHashMap<>();
        Map<String, List<SeatInfo>> exams = new LinkedHashMap<>();
        Map<String, String[]> names = new LinkedHashMap<>();
        jdbc.query(SEAT_SELECT + "where sa.run_id = ? order by s.roll, sl.slot_date, sl.session", rs -> {
            String roll = rs.getString(1);
            names.putIfAbsent(roll, new String[] {rs.getString(2), rs.getString(3)});
            exams.computeIfAbsent(roll, k -> new ArrayList<>()).add(seat(rs));
        }, runId);
        exams.forEach((roll, list) -> students.put(roll, new StudentTicket(roll, names.get(roll)[0],
                names.get(roll)[1], List.copyOf(list))));
        Map<String, List<RoomPlan>> rooms = new LinkedHashMap<>();
        List<String> slots = jdbc.query("select distinct to_char(sl.slot_date, 'YYYY-MM-DD') || '-' || sl.session, "
                + "sl.slot_date, sl.session from seat_assignment sa join exam_slot sl on sl.id = sa.slot_id "
                + "where sa.run_id = ? order by 2, 3", (rs, i) -> rs.getString(1), runId);
        for (String slot : slots) {
            rooms.put(slot, queries.rooms(runId, slot));
        }
        return new PlanSnapshot(runId, label, queries.timetable(runId), students, rooms,
                queries.invigilation(runId, null));
    }

    /** One student's exams in a run, straight from Postgres. */
    @Transactional(readOnly = true)
    public Optional<List<SeatInfo>> seatsOf(long runId, String roll) {
        List<SeatInfo> list = jdbc.query(SEAT_SELECT + "where sa.run_id = ? and s.roll = ? order by sl.slot_date, "
                + "sl.session", (rs, i) -> seat(rs), runId, roll);
        return list.isEmpty() ? Optional.empty() : Optional.of(list);
    }

    private static SeatInfo seat(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SeatInfo(rs.getString(4), rs.getString(5), rs.getString(6), rs.getDate(7).toLocalDate(),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11));
    }
}
