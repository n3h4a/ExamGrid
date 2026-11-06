package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Request and response bodies of the master-data API. */
public final class CatalogDtos {

    public static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,31}";

    private CatalogDtos() {
    }

    // ------------------------------------------------------------ courses

    public record CourseView(long id, String code, String name, String department, boolean hard,
                             Set<Session> allowedSessions, long candidates) {
        public static CourseView of(Course c, long candidates) {
            return new CourseView(c.getId(), c.getCode(), c.getName(), c.getDepartment(), c.isHard(),
                    c.getAllowedSessions(), candidates);
        }
    }

    public record CourseRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "must be 1-32 letters, digits, '.', '_' or '-'")
            String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 64) String department,
            Boolean hard,
            Set<Session> allowedSessions) {

        public boolean isHard() {
            return Boolean.TRUE.equals(hard);
        }
    }

    // ------------------------------------------------------------ students

    public record StudentView(long id, String roll, String name, String department, String email) {
        public static StudentView of(Student s) {
            return new StudentView(s.getId(), s.getRoll(), s.getName(), s.getDepartment(), s.getEmail());
        }
    }

    public record StudentDetail(StudentView student, List<String> courses) {
    }

    public record StudentRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "must be 1-32 letters, digits, '.', '_' or '-'")
            String roll,
            @Size(max = 120) String name,
            @Size(max = 64) String department,
            @Email @Size(max = 254) String email,
            List<@NotBlank String> courses) {
    }

    // ------------------------------------------------------------ rooms

    public record RoomView(long id, String code, String building, int rows, int columns, List<String> blockedSeats,
                           int capacity, int priority, boolean active) {
        public static RoomView of(Room r) {
            return new RoomView(r.getId(), r.getCode(), r.getBuilding(), r.getSeatRows(), r.getSeatColumns(),
                    r.getBlockedSeats(), r.getCapacity(), r.getPriority(), r.isActive());
        }
    }

    public record RoomRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "must be 1-32 letters, digits, '.', '_' or '-'")
            String code,
            @Size(max = 120) String building,
            @NotNull @Min(1) @Max(100) Integer rows,
            @NotNull @Min(1) @Max(100) Integer columns,
            List<@NotBlank String> blockedSeats,
            @Min(0) @Max(10_000) Integer priority,
            Boolean active) {
    }

    // ------------------------------------------------------------ faculty

    public record FacultyView(long id, String code, String name, String email, String department,
                              List<String> teaches, List<String> unavailableSlots) {
        public static FacultyView of(Faculty f) {
            return new FacultyView(f.getId(), f.getCode(), f.getName(), f.getEmail(), f.getDepartment(),
                    f.getTeaches().stream().map(Course::getCode).sorted().toList(),
                    f.getUnavailable().stream().sorted(Comparator.comparing(ExamSlot::getDate)
                            .thenComparing(ExamSlot::getSession)).map(ExamSlot::key).toList());
        }
    }

    public record FacultyRequest(
            @NotBlank @Pattern(regexp = CODE_PATTERN, message = "must be 1-32 letters, digits, '.', '_' or '-'")
            String code,
            @NotBlank @Size(max = 120) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 64) String department,
            List<@NotBlank String> teaches,
            List<@NotBlank String> unavailableSlots) {
    }

    // ------------------------------------------------------------ slots

    public record SlotView(long id, String key, LocalDate date, Session session) {
        public static SlotView of(ExamSlot s) {
            return new SlotView(s.getId(), s.key(), s.getDate(), s.getSession());
        }
    }

    public record SlotRequest(@NotNull LocalDate date, @NotNull Session session) {
    }

    /**
     * Creates every (date, session) between the two dates, skipping the given weekdays and
     * holidays. Existing slots are left alone.
     */
    public record GenerateSlotsRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty Set<Session> sessions,
            Set<DayOfWeek> skipWeekdays,
            Set<LocalDate> skipDates) {
    }

    // ------------------------------------------------------------ locks & groups

    public record LockView(String courseCode, String slotKey, String lockedBy, java.time.Instant lockedAt) {
        public static LockView of(CourseLock l) {
            return new LockView(l.getCourse().getCode(), l.getSlot().key(), l.getLockedBy(), l.getLockedAt());
        }
    }

    public record LockRequest(@NotBlank String slotKey) {
    }

    public record GroupView(long id, String name, List<String> courses) {
        public static GroupView of(SameSlotGroup g) {
            return new GroupView(g.getId(), g.getName(), g.getMembers().stream().map(Course::getCode).sorted().toList());
        }
    }

    public record GroupRequest(@NotBlank @Size(max = 120) String name,
                               @NotNull @Size(min = 2, message = "needs at least two courses") List<@NotBlank String> courses) {
    }

    // ------------------------------------------------------------ summary

    public record CatalogSummary(long students, long courses, long enrolments, long rooms, long activeRooms,
                                 long seats, long faculty, long slots, long locks, long sameSlotGroups) {
    }
}
