package com.examgrid.backend.demo;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.security.Role;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads a <em>synthetic</em> university (clearly fake names, generated with a fixed seed) into
 * an empty database so the demo deployment has something to schedule. Enabled with
 * {@code examgrid.demo.enabled=true}; it never touches a database that already has courses.
 *
 * <p>Demo accounts: {@code examcell2} (second exam cell member for two-person publishing),
 * the first student and the first faculty member, all with {@code examgrid.demo.password}.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String[] FIRST = {"Aarav", "Diya", "Kabir", "Meera", "Rohan", "Ananya", "Ishaan", "Saanvi",
            "Vihaan", "Aditi", "Arjun", "Kavya", "Reyansh", "Nisha", "Dev", "Tara", "Kiran", "Asha", "Ravi", "Zara"};
    private static final String[] LAST = {"Sharma", "Iyer", "Patel", "Reddy", "Khan", "Das", "Nair", "Gupta", "Rao",
            "Singh", "Menon", "Joshi", "Bose", "Kulkarni", "Fernandes", "Chopra"};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;
    private final int courses;
    private final int students;
    private final String password;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit, ObjectMapper mapper,
                          Clock clock,
                          @Value("${examgrid.demo.enabled:false}") boolean enabled,
                          @Value("${examgrid.demo.courses:120}") int courses,
                          @Value("${examgrid.demo.students:1500}") int students,
                          @Value("${examgrid.demo.password:demo12345}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
        this.enabled = enabled;
        this.courses = courses;
        this.students = students;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from course", Integer.class);
        if (!enabled || existing == null || existing > 0) {
            return;
        }
        long started = System.nanoTime();
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate start = today.plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        ProblemInstance demo = SyntheticInstanceGenerator.generate(
                new SyntheticInstanceGenerator.Spec(courses, students, 10, 2, start, 2026L));
        seed(demo);
        createUsers(demo);
        audit.record("demo.seeded", "catalog", null, Map.of("courses", demo.courseCount(),
                "students", demo.studentCount(), "enrolments", demo.enrolmentCount()));
        log.info("Seeded synthetic demo university: {} courses, {} students, {} enrolments in {} ms",
                demo.courseCount(), demo.studentCount(), demo.enrolmentCount(), (System.nanoTime() - started) / 1_000_000);
    }

    private void seed(ProblemInstance demo) {
        jdbc.batchUpdate("insert into course (code, name, department, hard, allowed_sessions) values (?, ?, ?, ?, ?)",
                demo.courses().stream().map(c -> new Object[] {c.code(), c.name(), department(c.code()), c.hard(),
                        c.allowedSessions().stream().map(Enum::name).sorted().collect(Collectors.joining(","))})
                        .toList());
        List<Object[]> studentRows = new ArrayList<>();
        for (int i = 0; i < demo.studentCount(); i++) {
            String roll = demo.student(i);
            studentRows.add(new Object[] {roll, FIRST[i % FIRST.length] + " " + LAST[(i / FIRST.length) % LAST.length],
                    department(roll.substring(2))});
        }
        jdbc.batchUpdate("insert into student (roll, name, department) values (?, ?, ?)", studentRows);
        jdbc.batchUpdate("insert into exam_slot (slot_date, session) values (?, ?)",
                demo.slots().stream().map(s -> new Object[] {Date.valueOf(s.date()), s.session().name()}).toList());
        int priority = 0;
        List<Object[]> roomRows = new ArrayList<>();
        for (Room r : demo.rooms()) {
            List<String> blocked = r.blockedSeats().stream().sorted().map(SeatPosition::label).toList();
            roomRows.add(new Object[] {r.code(), r.building(), r.rows(), r.columns(),
                    mapper.writeValueAsString(blocked), r.capacity(), ++priority});
        }
        jdbc.batchUpdate("insert into room (code, building, seat_rows, seat_columns, blocked_seats, capacity, priority) "
                + "values (?, ?, ?, ?, ?::jsonb, ?, ?)", roomRows);
        List<Object[]> facultyRows = new ArrayList<>();
        int n = 0;
        for (Faculty f : demo.faculty()) {
            String name = "Dr " + FIRST[(n * 7) % FIRST.length] + " " + LAST[(n * 3) % LAST.length];
            facultyRows.add(new Object[] {f.id(), name, f.id().toLowerCase() + "@demo.examgrid.dev",
                    department(f.teaches().stream().findFirst().orElse("GEN"))});
            n++;
        }
        jdbc.batchUpdate("insert into faculty (code, name, email, department) values (?, ?, ?, ?)", facultyRows);

        Map<String, Long> courseIds = ids("select id, code from course");
        Map<String, Long> studentIds = ids("select id, roll from student");
        Map<String, Long> facultyIds = ids("select id, code from faculty");
        Map<String, Long> slotIds = new HashMap<>();
        jdbc.query("select id, slot_date, session from exam_slot", rs -> {
            slotIds.put(Slot.of(rs.getDate(2).toLocalDate(),
                    com.examgrid.solver.model.Session.valueOf(rs.getString(3))).id(), rs.getLong(1));
        });

        List<Object[]> enrolments = new ArrayList<>();
        for (int c = 0; c < demo.courseCount(); c++) {
            long courseId = courseIds.get(demo.course(c).code());
            for (int s : demo.studentsOf(c)) {
                enrolments.add(new Object[] {studentIds.get(demo.student(s)), courseId});
            }
        }
        jdbc.batchUpdate("insert into enrolment (student_id, course_id) values (?, ?)", enrolments);
        List<Object[]> teaches = new ArrayList<>();
        List<Object[]> away = new ArrayList<>();
        for (Faculty f : demo.faculty()) {
            long id = facultyIds.get(f.id());
            f.teaches().forEach(code -> teaches.add(new Object[] {id, courseIds.get(code)}));
            f.unavailableSlotIds().forEach(slot -> away.add(new Object[] {id, slotIds.get(slot)}));
        }
        jdbc.batchUpdate("insert into faculty_course (faculty_id, course_id) values (?, ?)", teaches);
        jdbc.batchUpdate("insert into faculty_unavailability (faculty_id, slot_id) values (?, ?)", away);
    }

    private void createUsers(ProblemInstance demo) {
        String hash = encoder.encode(password);
        String student = demo.student(0);
        String faculty = demo.faculty().getFirst().id();
        jdbc.update("insert into app_user (username, password_hash, display_name, role) values (?, ?, ?, ?) "
                + "on conflict (username) do nothing", "examcell2", hash, "Second exam cell member", Role.EXAM_CELL.name());
        jdbc.update("insert into app_user (username, password_hash, display_name, role, student_roll) "
                + "values (?, ?, ?, ?, ?) on conflict (username) do nothing", student.toLowerCase(), hash,
                "Demo student " + student, Role.STUDENT.name(), student);
        jdbc.update("insert into app_user (username, password_hash, display_name, role, faculty_code) "
                + "values (?, ?, ?, ?, ?) on conflict (username) do nothing", faculty.toLowerCase(), hash,
                "Demo faculty " + faculty, Role.FACULTY.name(), faculty);
        log.info("Demo accounts: examcell2, {} (student), {} (faculty)", student.toLowerCase(), faculty.toLowerCase());
    }

    private Map<String, Long> ids(String sql) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getString(2), rs.getLong(1));
        });
        return out;
    }

    /** Department prefix of a course code or roll suffix, e.g. CS301 → CS. */
    private static String department(String code) {
        return code.replaceAll("[^A-Z].*$", "");
    }
}
