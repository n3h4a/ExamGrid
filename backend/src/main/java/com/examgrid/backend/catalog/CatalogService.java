package com.examgrid.backend.catalog;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.catalog.CatalogDtos.CatalogSummary;
import com.examgrid.backend.catalog.CatalogDtos.CourseRequest;
import com.examgrid.backend.catalog.CatalogDtos.FacultyRequest;
import com.examgrid.backend.catalog.CatalogDtos.GenerateSlotsRequest;
import com.examgrid.backend.catalog.CatalogDtos.GroupRequest;
import com.examgrid.backend.catalog.CatalogDtos.RoomRequest;
import com.examgrid.backend.catalog.CatalogDtos.StudentRequest;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.security.CurrentUser;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Create/update/delete for master data. Every mutation is validated, normalised (codes are
 * upper-cased) and audited in the same transaction.
 */
@Service
public class CatalogService {

    private static final int MAX_GENERATED_SLOTS = 300;

    private final CourseRepository courses;
    private final StudentRepository students;
    private final RoomRepository rooms;
    private final FacultyRepository faculty;
    private final ExamSlotRepository slots;
    private final CourseLockRepository locks;
    private final SameSlotGroupRepository groups;
    private final EnrolmentRepository enrolments;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public CatalogService(CourseRepository courses, StudentRepository students, RoomRepository rooms,
                          FacultyRepository faculty, ExamSlotRepository slots, CourseLockRepository locks,
                          SameSlotGroupRepository groups, EnrolmentRepository enrolments, JdbcTemplate jdbc,
                          AuditService audit, Clock clock) {
        this.courses = courses;
        this.students = students;
        this.rooms = rooms;
        this.faculty = faculty;
        this.slots = slots;
        this.locks = locks;
        this.groups = groups;
        this.enrolments = enrolments;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    public static String normaliseCode(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ courses

    @Transactional
    public Course createCourse(CourseRequest r) {
        String code = normaliseCode(r.code());
        if (courses.findByCode(code).isPresent()) {
            throw ApiException.conflict("duplicate_code", "Course " + code + " already exists.");
        }
        Course c = new Course(code, clock.instant());
        c.update(r.name().strip(), strip(r.department()), r.isHard(), r.allowedSessions(), clock.instant());
        courses.save(c);
        audit.record("course.created", "course", code, Map.of("name", c.getName()));
        return c;
    }

    @Transactional
    public Course updateCourse(long id, CourseRequest r) {
        Course c = courses.findById(id).orElseThrow(() -> ApiException.notFound("Course", id));
        requireSameCode(c.getCode(), r.code());
        Map<String, Object> before = Map.of("name", c.getName(), "hard", c.isHard(),
                "sessions", c.getAllowedSessions().toString());
        c.update(r.name().strip(), strip(r.department()), r.isHard(), r.allowedSessions(), clock.instant());
        audit.record("course.updated", "course", c.getCode(), Map.of("before", before, "after", Map.of(
                "name", c.getName(), "hard", c.isHard(), "sessions", c.getAllowedSessions().toString())));
        return c;
    }

    @Transactional
    public void deleteCourse(long id) {
        Course c = courses.findById(id).orElseThrow(() -> ApiException.notFound("Course", id));
        courses.delete(c);
        courses.flush();
        audit.record("course.deleted", "course", c.getCode(), Map.of());
    }

    // ------------------------------------------------------------ students

    @Transactional
    public Student createStudent(StudentRequest r) {
        String roll = normaliseCode(r.roll());
        if (students.findByRoll(roll).isPresent()) {
            throw ApiException.conflict("duplicate_roll", "Student " + roll + " already exists.");
        }
        Student s = new Student(roll, clock.instant());
        s.update(strip(r.name()), strip(r.department()), strip(r.email()));
        students.save(s);
        replaceEnrolments(s, r.courses());
        audit.record("student.created", "student", roll, Map.of("courses", r.courses() == null ? List.of() : r.courses()));
        return s;
    }

    @Transactional
    public Student updateStudent(long id, StudentRequest r) {
        Student s = students.findById(id).orElseThrow(() -> ApiException.notFound("Student", id));
        requireSameCode(s.getRoll(), r.roll());
        s.update(strip(r.name()), strip(r.department()), strip(r.email()));
        if (r.courses() != null) {
            replaceEnrolments(s, r.courses());
        }
        audit.record("student.updated", "student", s.getRoll(), Map.of("name", s.getName()));
        return s;
    }

    @Transactional
    public void deleteStudent(long id) {
        Student s = students.findById(id).orElseThrow(() -> ApiException.notFound("Student", id));
        students.delete(s);
        students.flush();
        audit.record("student.deleted", "student", s.getRoll(), Map.of());
    }

    private void replaceEnrolments(Student s, List<String> codes) {
        if (codes == null) {
            return;
        }
        Set<String> wanted = codes.stream().map(CatalogService::normaliseCode).collect(Collectors.toSet());
        List<Course> found = courses.findAllByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new TreeSet<>(wanted);
            found.forEach(c -> missing.remove(c.getCode()));
            throw ApiException.badRequest("unknown_course", "Unknown course(s): " + String.join(", ", missing));
        }
        jdbc.update("delete from enrolment where student_id = ?", s.getId());
        for (Course c : found) {
            jdbc.update("insert into enrolment (student_id, course_id) values (?, ?)", s.getId(), c.getId());
        }
    }

    // ------------------------------------------------------------ rooms

    @Transactional
    public Room createRoom(RoomRequest r) {
        String code = normaliseCode(r.code());
        if (rooms.findByCode(code).isPresent()) {
            throw ApiException.conflict("duplicate_code", "Room " + code + " already exists.");
        }
        Room room = new Room(code);
        applyRoom(room, r);
        rooms.save(room);
        audit.record("room.created", "room", code, Map.of("capacity", room.getCapacity()));
        return room;
    }

    @Transactional
    public Room updateRoom(long id, RoomRequest r) {
        Room room = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room", id));
        requireSameCode(room.getCode(), r.code());
        int before = room.getCapacity();
        applyRoom(room, r);
        audit.record("room.updated", "room", room.getCode(), Map.of("capacityBefore", before,
                "capacityAfter", room.getCapacity(), "active", room.isActive()));
        return room;
    }

    @Transactional
    public void deleteRoom(long id) {
        Room room = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room", id));
        rooms.delete(room);
        rooms.flush();
        audit.record("room.deleted", "room", room.getCode(), Map.of());
    }

    /** Validates a seat grid and its blocked seats; returns canonical labels. */
    public static List<String> validateLayout(int rowCount, int columnCount, List<String> blocked) {
        Set<SeatPosition> seen = new LinkedHashSet<>();
        for (String label : blocked == null ? List.<String>of() : blocked) {
            SeatPosition p;
            try {
                p = SeatPosition.parse(label);
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("invalid_seat", "'" + label + "' is not a seat label like A1.");
            }
            if (p.row() >= rowCount || p.column() >= columnCount) {
                throw ApiException.badRequest("invalid_seat", "Seat " + p.label() + " is outside the "
                        + rowCount + " × " + columnCount + " grid.");
            }
            seen.add(p);
        }
        if (seen.size() >= rowCount * columnCount) {
            throw ApiException.badRequest("no_usable_seats", "A room needs at least one usable seat.");
        }
        return seen.stream().sorted().map(SeatPosition::label).toList();
    }

    private void applyRoom(Room room, RoomRequest r) {
        List<String> blocked = validateLayout(r.rows(), r.columns(), r.blockedSeats());
        room.update(strip(r.building()), r.rows(), r.columns(), blocked, r.priority() == null ? 0 : r.priority(),
                r.active() == null || r.active());
    }

    // ------------------------------------------------------------ faculty

    @Transactional
    public Faculty createFaculty(FacultyRequest r) {
        String code = normaliseCode(r.code());
        if (faculty.findByCode(code).isPresent()) {
            throw ApiException.conflict("duplicate_code", "Faculty " + code + " already exists.");
        }
        Faculty f = new Faculty(code);
        applyFaculty(f, r);
        faculty.save(f);
        audit.record("faculty.created", "faculty", code, Map.of("teaches", r.teaches() == null ? List.of() : r.teaches()));
        return f;
    }

    @Transactional
    public Faculty updateFaculty(long id, FacultyRequest r) {
        Faculty f = faculty.findById(id).orElseThrow(() -> ApiException.notFound("Faculty", id));
        requireSameCode(f.getCode(), r.code());
        applyFaculty(f, r);
        audit.record("faculty.updated", "faculty", f.getCode(), Map.of(
                "teaches", f.getTeaches().stream().map(Course::getCode).sorted().toList(),
                "unavailable", f.getUnavailable().stream().map(ExamSlot::key).sorted().toList()));
        return f;
    }

    @Transactional
    public void deleteFaculty(long id) {
        Faculty f = faculty.findById(id).orElseThrow(() -> ApiException.notFound("Faculty", id));
        faculty.delete(f);
        faculty.flush();
        audit.record("faculty.deleted", "faculty", f.getCode(), Map.of());
    }

    private void applyFaculty(Faculty f, FacultyRequest r) {
        Set<Course> teaches = resolveCourses(r.teaches());
        Set<ExamSlot> away = new HashSet<>();
        for (String key : r.unavailableSlots() == null ? List.<String>of() : r.unavailableSlots()) {
            away.addAll(resolveSlotsForKey(key));
        }
        f.update(r.name().strip(), strip(r.email()), strip(r.department()), teaches, away);
    }

    /** A key with a session names one slot; a bare date names every slot that day. */
    public List<ExamSlot> resolveSlotsForKey(String key) {
        SlotKeys.Key k = SlotKeys.parse(key).orElseThrow(() -> ApiException.badRequest("invalid_slot",
                "'" + key + "' is not a slot like 2026-12-01-MORNING or a date like 2026-12-01."));
        List<ExamSlot> out = new ArrayList<>();
        for (ExamSlot s : slots.findAllByOrderByDateAscSessionAsc()) {
            if (s.getDate().equals(k.date()) && (k.session() == null || s.getSession() == k.session())) {
                out.add(s);
            }
        }
        if (out.isEmpty()) {
            throw ApiException.badRequest("unknown_slot", "No exam slot matches " + key + ".");
        }
        return out;
    }

    private Set<Course> resolveCourses(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = codes.stream().map(CatalogService::normaliseCode).collect(Collectors.toSet());
        List<Course> found = courses.findAllByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new TreeSet<>(wanted);
            found.forEach(c -> missing.remove(c.getCode()));
            throw ApiException.badRequest("unknown_course", "Unknown course(s): " + String.join(", ", missing));
        }
        return new HashSet<>(found);
    }

    // ------------------------------------------------------------ slots

    @Transactional
    public ExamSlot createSlot(LocalDate date, Session session) {
        if (slots.findByDateAndSession(date, session).isPresent()) {
            throw ApiException.conflict("duplicate_slot", "Slot " + date + " " + session + " already exists.");
        }
        ExamSlot slot = slots.save(new ExamSlot(date, session));
        audit.record("slot.created", "slot", slot.key(), Map.of());
        return slot;
    }

    @Transactional
    public List<ExamSlot> generateSlots(GenerateSlotsRequest r) {
        if (r.endDate().isBefore(r.startDate())) {
            throw ApiException.badRequest("invalid_range", "endDate must not be before startDate.");
        }
        long days = ChronoUnit.DAYS.between(r.startDate(), r.endDate()) + 1;
        if (days * r.sessions().size() > MAX_GENERATED_SLOTS) {
            throw ApiException.badRequest("too_many_slots", "At most " + MAX_GENERATED_SLOTS + " slots per request.");
        }
        Set<DayOfWeek> skipDays = r.skipWeekdays() == null ? Set.of() : r.skipWeekdays();
        Set<LocalDate> holidays = r.skipDates() == null ? Set.of() : r.skipDates();
        Map<String, ExamSlot> existing = new HashMap<>();
        slots.findAll().forEach(s -> existing.put(s.key(), s));
        List<ExamSlot> created = new ArrayList<>();
        for (LocalDate d = r.startDate(); !d.isAfter(r.endDate()); d = d.plusDays(1)) {
            if (skipDays.contains(d.getDayOfWeek()) || holidays.contains(d)) {
                continue;
            }
            for (Session s : EnumSet.copyOf(r.sessions())) {
                ExamSlot slot = new ExamSlot(d, s);
                if (!existing.containsKey(slot.key())) {
                    created.add(slots.save(slot));
                }
            }
        }
        audit.record("slot.generated", "slot", null, Map.of("created", created.size(),
                "from", r.startDate().toString(), "to", r.endDate().toString()));
        return created;
    }

    @Transactional
    public void deleteSlot(long id) {
        ExamSlot slot = slots.findById(id).orElseThrow(() -> ApiException.notFound("Slot", id));
        slots.delete(slot);
        slots.flush();
        audit.record("slot.deleted", "slot", slot.key(), Map.of());
    }

    // ------------------------------------------------------------ locks

    @Transactional
    public CourseLock lock(String courseCode, String slotKey) {
        Course course = courses.findByCode(normaliseCode(courseCode))
                .orElseThrow(() -> ApiException.notFound("Course", courseCode));
        ExamSlot slot = slots.findByKey(slotKey).orElseThrow(() -> ApiException.badRequest("unknown_slot",
                "No exam slot " + slotKey + "."));
        if (!course.getAllowedSessions().isEmpty() && !course.getAllowedSessions().contains(slot.getSession())) {
            throw ApiException.badRequest("session_not_allowed", course.getCode() + " may only be held in "
                    + course.getAllowedSessions() + " sessions.");
        }
        String actor = CurrentUser.actorName();
        CourseLock lock = locks.findById(course.getId())
                .map(l -> {
                    l.relock(slot, actor, clock.instant());
                    return l;
                })
                .orElseGet(() -> locks.save(new CourseLock(course, slot, actor, clock.instant())));
        audit.record("course.locked", "course", course.getCode(), Map.of("slot", slot.key()));
        return lock;
    }

    @Transactional
    public void unlock(String courseCode) {
        Course course = courses.findByCode(normaliseCode(courseCode))
                .orElseThrow(() -> ApiException.notFound("Course", courseCode));
        CourseLock lock = locks.findById(course.getId())
                .orElseThrow(() -> ApiException.notFound("Lock for course", course.getCode()));
        locks.delete(lock);
        audit.record("course.unlocked", "course", course.getCode(), Map.of());
    }

    // ------------------------------------------------------------ same-slot groups

    @Transactional
    public SameSlotGroup createGroup(GroupRequest r) {
        if (groups.existsByNameIgnoreCase(r.name().strip())) {
            throw ApiException.conflict("duplicate_name", "A group named " + r.name() + " already exists.");
        }
        SameSlotGroup g = new SameSlotGroup(r.name().strip());
        g.update(r.name().strip(), groupMembers(r, null));
        groups.save(g);
        audit.record("group.created", "same_slot_group", g.getId(), Map.of("courses", r.courses()));
        return g;
    }

    @Transactional
    public SameSlotGroup updateGroup(long id, GroupRequest r) {
        SameSlotGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Group", id));
        g.update(r.name().strip(), groupMembers(r, g.getId()));
        audit.record("group.updated", "same_slot_group", g.getId(), Map.of("courses", r.courses()));
        return g;
    }

    @Transactional
    public void deleteGroup(long id) {
        SameSlotGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Group", id));
        groups.delete(g);
        audit.record("group.deleted", "same_slot_group", id, Map.of("name", g.getName()));
    }

    private Set<Course> groupMembers(GroupRequest r, Long selfId) {
        Set<Course> members = resolveCourses(r.courses());
        if (members.size() < 2) {
            throw ApiException.badRequest("group_too_small", "A same-slot group needs at least two courses.");
        }
        for (SameSlotGroup other : groups.findAllByOrderByNameAsc()) {
            if (other.getId().equals(selfId)) {
                continue;
            }
            for (Course c : other.getMembers()) {
                if (members.contains(c)) {
                    throw ApiException.conflict("course_in_other_group", c.getCode() + " already belongs to group "
                            + other.getName() + ".");
                }
            }
        }
        return members;
    }

    // ------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public CatalogSummary summary() {
        Long seats = jdbc.queryForObject("select coalesce(sum(capacity), 0) from room where active", Long.class);
        Long activeRooms = jdbc.queryForObject("select count(*) from room where active", Long.class);
        return new CatalogSummary(students.count(), courses.count(), enrolments.count(), rooms.count(),
                activeRooms == null ? 0 : activeRooms, seats == null ? 0 : seats, faculty.count(), slots.count(),
                locks.count(), groups.count());
    }

    /** Candidates per course id. */
    @Transactional(readOnly = true)
    public Map<Long, Long> candidatesPerCourse() {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : enrolments.countPerCourse()) {
            out.put((Long) row[0], (Long) row[1]);
        }
        return out;
    }

    private static void requireSameCode(String existing, String requested) {
        if (!existing.equals(normaliseCode(requested))) {
            throw ApiException.badRequest("code_immutable", "Codes cannot be changed (" + existing + ").");
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
