package com.examgrid.backend.scheduling;

import com.examgrid.solver.pipeline.ExamAssignment;
import com.examgrid.solver.pipeline.InvigilationAssignment;
import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists a solver {@link Solution} as rows of {@code exam_assignment}, {@code seat_assignment}
 * and {@code invigilation} with JDBC batches (a plan for 2,400 students is ~13,000 seat rows).
 * Codes are resolved to ids once up front. The caller supplies the transaction.
 */
@Component
public class PlanWriter {

    private final JdbcTemplate jdbc;

    public PlanWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(long runId, Solution solution) {
        Map<String, Long> courses = ids("select id, code from course");
        Map<String, Long> students = ids("select id, roll from student");
        Map<String, Long> rooms = ids("select id, code from room");
        Map<String, Long> faculty = ids("select id, code from faculty");
        Map<String, Long> slots = new HashMap<>();
        jdbc.query("select id, slot_date, session from exam_slot", rs -> {
            slots.put(rs.getDate(2).toLocalDate() + "-" + rs.getString(3), rs.getLong(1));
        });

        List<Object[]> exams = new ArrayList<>();
        for (ExamAssignment e : solution.exams()) {
            exams.add(new Object[] {runId, require(courses, e.courseCode(), "course"), require(slots, e.slotId(), "slot")});
        }
        jdbc.batchUpdate("insert into exam_assignment (run_id, course_id, slot_id) values (?, ?, ?)", exams);

        List<Object[]> seats = new ArrayList<>(solution.seats().size());
        for (SeatAssignment s : solution.seats()) {
            seats.add(new Object[] {runId, require(students, s.roll(), "student"), require(courses, s.courseCode(), "course"),
                    require(slots, s.slotId(), "slot"), require(rooms, s.roomCode(), "room"), s.seat().row(),
                    s.seat().column(), s.seatLabel()});
        }
        jdbc.batchUpdate("insert into seat_assignment (run_id, student_id, course_id, slot_id, room_id, seat_row, "
                + "seat_col, seat_label) values (?, ?, ?, ?, ?, ?, ?, ?)", seats);

        List<Object[]> duties = new ArrayList<>();
        for (InvigilationAssignment i : solution.invigilations()) {
            duties.add(new Object[] {runId, require(faculty, i.facultyId(), "faculty"), require(rooms, i.roomCode(), "room"),
                    require(slots, i.slotId(), "slot")});
        }
        jdbc.batchUpdate("insert into invigilation (run_id, faculty_id, room_id, slot_id) values (?, ?, ?, ?)", duties);
    }

    private Map<String, Long> ids(String sql) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getString(2), rs.getLong(1));
        });
        return out;
    }

    private static long require(Map<String, Long> ids, String key, String what) {
        Long id = ids.get(key);
        if (id == null) {
            throw new IllegalStateException("The " + what + " " + key + " was deleted while the run was solving.");
        }
        return id;
    }
}
