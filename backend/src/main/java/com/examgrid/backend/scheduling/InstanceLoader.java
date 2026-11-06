package com.examgrid.backend.scheduling;

import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;
import com.examgrid.solver.model.SolverParams;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anti-corruption layer between the database and the framework-free solver: reads a
 * consistent snapshot of the catalog (one read-only, repeatable-read transaction) and builds
 * a {@link ProblemInstance}. It also fingerprints the input, so the UI can tell whether the
 * data has changed since a run was made.
 */
@Component
public class InstanceLoader {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InstanceLoader(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Counts and fingerprint of the input a run was solved on. */
    public record InputSummary(int courses, int examinedCourses, int students, int enrolments, int slots, int rooms,
                               int seats, int faculty, int locks, int sameSlotGroups, String fingerprint) {
    }

    /** A snapshot ready to solve. */
    public record Snapshot(ProblemInstance instance, InputSummary summary) {
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot load(SolverParams params) {
        ProblemInstance.Builder b = ProblemInstance.builder().params(params);
        MessageDigest digest = sha256();

        jdbc.query("select code, name, hard, allowed_sessions from course order by code", rs -> {
            Set<Session> sessions = Arrays.stream(rs.getString(4).split(",")).filter(s -> !s.isBlank())
                    .map(Session::valueOf).collect(Collectors.toSet());
            b.course(new Course(rs.getString(1), rs.getString(2), rs.getBoolean(3), sessions));
            update(digest, "C", rs.getString(1), rs.getBoolean(3), rs.getString(4));
        });
        int[] enrolments = {0};
        Set<String> students = new HashSet<>();
        Set<String> examined = new HashSet<>();
        jdbc.query("select s.roll, c.code from enrolment e join student s on s.id = e.student_id "
                + "join course c on c.id = e.course_id order by s.roll, c.code", rs -> {
            b.enrol(rs.getString(1), rs.getString(2));
            students.add(rs.getString(1));
            examined.add(rs.getString(2));
            enrolments[0]++;
            update(digest, "E", rs.getString(1), rs.getString(2));
        });
        Map<Long, String> slotKeys = new HashMap<>();
        jdbc.query("select id, slot_date, session from exam_slot order by slot_date, session", rs -> {
            Slot slot = Slot.of(rs.getDate(2).toLocalDate(), Session.valueOf(rs.getString(3)));
            slotKeys.put(rs.getLong(1), slot.id());
            b.slot(slot);
            update(digest, "S", slot.id());
        });
        int[] seats = {0};
        int[] rooms = {0};
        jdbc.query("select code, building, seat_rows, seat_columns, blocked_seats, capacity from room "
                + "where active order by priority, code", rs -> {
            List<String> blocked = mapper.readValue(rs.getString(5), STRING_LIST);
            Set<SeatPosition> positions = blocked.stream().map(SeatPosition::parse).collect(Collectors.toSet());
            b.room(new Room(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), positions));
            seats[0] += rs.getInt(6);
            rooms[0]++;
            update(digest, "R", rs.getString(1), rs.getInt(3), rs.getInt(4), String.join(";", blocked));
        });

        Map<String, String[]> facultyRows = new LinkedHashMap<>();
        Map<String, Set<String>> teaches = new HashMap<>();
        Map<String, Set<String>> away = new HashMap<>();
        jdbc.query("select code, name from faculty order by code", rs -> {
            facultyRows.put(rs.getString(1), new String[] {rs.getString(2)});
        });
        jdbc.query("select f.code, c.code from faculty_course fc join faculty f on f.id = fc.faculty_id "
                + "join course c on c.id = fc.course_id order by 1, 2", rs -> {
            teaches.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>()).add(rs.getString(2));
        });
        jdbc.query("select f.code, fu.slot_id from faculty_unavailability fu join faculty f on f.id = fu.faculty_id "
                + "order by 1, 2", rs -> {
            away.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>()).add(slotKeys.get(rs.getLong(2)));
        });
        facultyRows.forEach((code, row) -> {
            Set<String> t = teaches.getOrDefault(code, Set.of());
            Set<String> a = away.getOrDefault(code, Set.of());
            b.faculty(new Faculty(code, row[0], t, a));
            update(digest, "F", code, String.join(";", t), String.join(";", a));
        });

        int[] locks = {0};
        jdbc.query("select c.code, l.slot_id from course_lock l join course c on c.id = l.course_id order by 1", rs -> {
            b.lock(rs.getString(1), slotKeys.get(rs.getLong(2)));
            locks[0]++;
            update(digest, "L", rs.getString(1), slotKeys.get(rs.getLong(2)));
        });
        Map<Long, List<String>> groups = new LinkedHashMap<>();
        jdbc.query("select m.group_id, c.code from same_slot_group_member m join course c on c.id = m.course_id "
                + "order by 1, 2", rs -> {
            groups.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        });
        groups.values().forEach(codes -> {
            b.sameSlot(new LinkedHashSet<>(codes));
            update(digest, "G", String.join(";", codes));
        });

        ProblemInstance instance = b.build();
        InputSummary summary = new InputSummary(instance.courseCount(), examined.size(), students.size(),
                enrolments[0], instance.slotCount(), rooms[0], seats[0], facultyRows.size(), locks[0], groups.size(),
                java.util.HexFormat.of().formatHex(digest.digest()));
        return new Snapshot(instance, summary);
    }

    /** Fingerprint of the current data, to compare with a run's stored fingerprint. */
    public String currentFingerprint() {
        return load(SolverParams.defaults()).summary().fingerprint();
    }

    private static void update(MessageDigest digest, String type, Object... values) {
        StringBuilder sb = new StringBuilder(type);
        for (Object v : values) {
            sb.append('\u001f').append(v);
        }
        sb.append('\n');
        digest.update(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
