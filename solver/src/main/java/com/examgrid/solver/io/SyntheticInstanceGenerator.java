package com.examgrid.solver.io;

import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;
import com.examgrid.solver.model.SolverParams;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Generates realistic, reproducible synthetic universities for tests, benchmarks and the demo.
 *
 * <p>Structure mirrors real enrolment data: students belong to a department and year
 * (a cohort), take every core course of their cohort plus one elective, and about 8 % carry
 * a backlog paper from the previous year. That gives the dense-within-cohort, sparse-between
 * conflict graphs real exam cells see, instead of uniformly random graphs.
 */
public final class SyntheticInstanceGenerator {

    private static final String[] DEPARTMENTS = {"CS", "EC", "ME", "CE", "EE", "IT", "CH", "BT", "MA", "PH"};
    private static final int YEARS = 4;

    /**
     * @param courses        approximate number of courses
     * @param students       number of students
     * @param days           number of exam days
     * @param sessionsPerDay 1–3 sessions per day
     * @param start          first exam day (Sundays are skipped)
     * @param seed           random seed
     */
    public record Spec(int courses, int students, int days, int sessionsPerDay, LocalDate start, long seed) {
        public Spec {
            if (courses < YEARS || students < 1 || days < 1 || sessionsPerDay < 1 || sessionsPerDay > 3) {
                throw new IllegalArgumentException("invalid synthetic instance spec");
            }
        }

        public static Spec of(int courses, int students) {
            return new Spec(courses, students, 10, 2, LocalDate.of(2026, 12, 1), 7L);
        }
    }

    private SyntheticInstanceGenerator() {
    }

    public static ProblemInstance generate(Spec spec) {
        return builder(spec).build();
    }

    public static ProblemInstance.Builder builder(Spec spec) {
        SplittableRandom rnd = new SplittableRandom(spec.seed());
        ProblemInstance.Builder b = ProblemInstance.builder().params(SolverParams.defaults());

        int departments = Math.max(1, Math.min(DEPARTMENTS.length, spec.courses() / (YEARS * 6)));
        int cohorts = departments * YEARS;
        int perCohort = Math.max(1, spec.courses() / cohorts);
        int electives = Math.max(perCohort >= 6 ? 2 : 0, perCohort / 4);
        int core = Math.max(1, perCohort - electives);

        List<List<String>> coreOf = new ArrayList<>();
        List<List<String>> electivesOf = new ArrayList<>();
        List<String> allCourses = new ArrayList<>();
        for (int d = 0; d < departments; d++) {
            for (int y = 1; y <= YEARS; y++) {
                List<String> cores = new ArrayList<>();
                List<String> elects = new ArrayList<>();
                for (int i = 1; i <= core + electives; i++) {
                    String code = DEPARTMENTS[d] + y + String.format("%02d", i);
                    boolean hard = i <= core && rnd.nextInt(100) < 25;
                    boolean lab = i == core && core > 2 && rnd.nextInt(100) < 40;
                    Set<Session> sessions = lab && spec.sessionsPerDay() > 1 ? Set.of(Session.AFTERNOON) : Set.of();
                    String title = (lab ? "Laboratory " : "") + DEPARTMENTS[d] + " paper " + y + "." + i;
                    b.course(new Course(code, title, hard, sessions));
                    (i <= core ? cores : elects).add(code);
                    allCourses.add(code);
                }
                coreOf.add(cores);
                electivesOf.add(elects);
            }
        }

        int admissionYear = spec.start().getYear() % 100;
        int[] serial = new int[cohorts];
        for (int s = 0; s < spec.students(); s++) {
            int cohort = s % cohorts;
            int dept = cohort / YEARS;
            int year = cohort % YEARS + 1;
            String roll = String.format("%02d%s%03d", admissionYear - year + 1, DEPARTMENTS[dept], ++serial[cohort]);
            coreOf.get(cohort).forEach(c -> b.enrol(roll, c));
            List<String> pool = electivesOf.get(cohort);
            if (!pool.isEmpty()) {
                b.enrol(roll, pool.get(rnd.nextInt(pool.size())));
            }
            if (year > 1 && rnd.nextInt(100) < 8) {
                List<String> previous = coreOf.get(cohort - 1);
                b.enrol(roll, previous.get(rnd.nextInt(previous.size())));
            }
        }

        List<Slot> slots = slots(spec);
        b.slots(slots);

        // Size rooms for the busiest plausible slot: enough seats for about 2.5× the average
        // slot load, with a floor of twice the largest cohort.
        int perStudent = core + (electives > 0 ? 1 : 0);
        long enrolments = (long) spec.students() * perStudent;
        int cohortSize = (spec.students() + cohorts - 1) / cohorts;
        long targetSeats = Math.max((long) (2.5 * enrolments / slots.size()), 2L * cohortSize + 60);
        int[][] layouts = {{6, 8}, {8, 10}, {5, 6}, {10, 12}};
        long seats = 0;
        int roomNo = 0;
        while (seats < targetSeats) {
            int[] layout = layouts[roomNo % layouts.length];
            String building = "Block " + (char) ('A' + roomNo / 8);
            Set<SeatPosition> blocked = new HashSet<>();
            if (layout[0] >= 8 && rnd.nextInt(100) < 30) {
                blocked.add(new SeatPosition(layout[0] - 1, layout[1] - 1));
            }
            Room room = new Room(String.format("R%d%02d", 1 + roomNo / 8, 1 + roomNo % 8), building,
                    layout[0], layout[1], blocked);
            b.room(room);
            seats += room.capacity();
            roomNo++;
        }

        int facultyCount = Math.max(roomNo * 3, allCourses.size() / 2 + 1);
        for (int f = 0; f < facultyCount; f++) {
            Set<String> teaches = new HashSet<>();
            int load = 1 + rnd.nextInt(2);
            for (int i = 0; i < load; i++) {
                teaches.add(allCourses.get(rnd.nextInt(allCourses.size())));
            }
            Set<String> away = new HashSet<>();
            if (rnd.nextInt(100) < 15) {
                away.add(slots.get(rnd.nextInt(slots.size())).id());
            }
            b.faculty(new Faculty(String.format("F%03d", f + 1), "Faculty " + (f + 1), teaches, away));
        }
        return b;
    }

    public static List<Slot> slots(Spec spec) {
        List<Slot> slots = new ArrayList<>();
        LocalDate day = spec.start();
        Session[] sessions = Session.values();
        int added = 0;
        while (added < spec.days()) {
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (int s = 0; s < spec.sessionsPerDay(); s++) {
                    slots.add(Slot.of(day, sessions[s]));
                }
                added++;
            }
            day = day.plusDays(1);
        }
        return slots;
    }
}
