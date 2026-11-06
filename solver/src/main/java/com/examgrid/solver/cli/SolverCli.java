package com.examgrid.solver.cli;

import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.io.InstanceCsvLoader;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.OptimizerType;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Slot;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.optimize.TimetableMetrics;
import com.examgrid.solver.pipeline.ExamGridSolver;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionMetrics;
import com.examgrid.solver.pipeline.TimetableStage;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.ProgressListener;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command-line entry point: CSV (or synthetic data) in, exam plan out. With rooms the full
 * pipeline runs (timetable, seats, invigilators); without rooms only the timetable is built.
 *
 * <pre>
 * java -jar examgrid-solver.jar --enrolments enrolments.csv [--courses courses.csv]
 *      [--rooms rooms.csv] [--faculty faculty.csv] [--days 10] [--sessions 2] [--start 2026-12-01]
 * java -jar examgrid-solver.jar --synthetic 180:2400
 * </pre>
 */
public final class SolverCli {

    private final PrintStream out;
    private final PrintStream err;

    SolverCli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        System.exit(new SolverCli(System.out, System.err).run(args));
    }

    int run(String[] args) {
        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            usage();
            return 2;
        }
        if (options.containsKey("help") || (!options.containsKey("enrolments") && !options.containsKey("synthetic"))) {
            usage();
            return options.containsKey("help") ? 0 : 2;
        }
        try {
            ProblemInstance instance = load(options);
            return solve(instance, options.containsKey("out") ? Path.of(options.get("out")) : null);
        } catch (IllegalArgumentException e) {
            err.println("Invalid input: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("Could not read input: " + e.getMessage());
            return 2;
        }
    }

    private int solve(ProblemInstance instance, Path outDir) throws IOException {
        out.printf("Loaded %d courses, %d students, %d enrolments, %d slots, %d rooms, %d faculty%n",
                instance.courseCount(), instance.studentCount(), instance.enrolmentCount(),
                instance.slotCount(), instance.roomCount(), instance.faculty().size());
        try {
            if (instance.roomCount() == 0) {
                TimetableStage.Outcome result = new TimetableStage().run(instance, ProgressListener.NONE,
                        CancellationToken.none());
                printTimetableSummary(instance, result.dsaturColours(), result.repaired(),
                        result.optimisation().initialScore(), result.optimisation().iterations(),
                        result.optimisation().stopReason().name(), result.metrics());
                out.println("(no rooms given: seating and invigilation skipped)");
                out.println();
                Map<String, String> slotOf = new HashMap<>();
                for (int c = 0; c < instance.courseCount(); c++) {
                    slotOf.put(instance.course(c).code(), instance.slot(result.slotOfCourse(c)).id());
                }
                printTimetable(instance, slotOf);
                return 0;
            }
            Solution solution = new ExamGridSolver().solve(instance);
            SolutionMetrics m = solution.metrics();
            printTimetableSummary(instance, m.dsaturColours(), m.repaired(), m.initialScore(),
                    m.optimiserIterations(), m.optimiserStop(), m.timetable());
            out.printf("Rooms: %d room-slots used, %.0f%% of their seats filled | invigilators %d assigned,"
                            + " %d unfilled, %d-%d duties each%n",
                    m.roomSlotsUsed(), 100 * m.seatUtilisation(), m.invigilationDuties(), m.unfilledDuties(),
                    m.minFacultyDuties(), m.maxFacultyDuties());
            solution.warnings().forEach(w -> out.println("Warning: " + w));
            out.printf("Solved in %d ms%n%n", m.elapsedMillis());
            Map<String, String> slotOf = new HashMap<>();
            solution.exams().forEach(e -> slotOf.put(e.courseCode(), e.slotId()));
            printTimetable(instance, slotOf);
            if (outDir != null) {
                SolutionCsvWriter.write(solution, outDir);
                out.println();
                out.println("Wrote timetable.csv, seats.csv and invigilation.csv to " + outDir);
            }
            return 0;
        } catch (InfeasibleException e) {
            err.println(e.report().summary());
            return 1;
        }
    }

    private void printTimetableSummary(ProblemInstance instance, int colours, boolean repaired, long initialScore,
                                       long iterations, String stop, TimetableMetrics m) {
        out.printf("DSatur: clash-free using %d of %d slots%s%n", colours, instance.slotCount(),
                repaired ? " (TabuCol repair needed)" : "");
        out.printf("%s: score %d -> %d in %d iterations (%s)%n", instance.params().optimizer(),
                initialScore, m.score(), iterations, stop);
        out.printf("Clashes %d | same-day pairs %d (%d students) | back-to-back pairs %d | hard-gap pairs %d"
                        + " | max exams per day %d%n",
                m.clashPairs(), m.sameDayPairs(), m.studentsWithSameDay(), m.backToBackPairs(), m.hardGapPairs(),
                m.maxExamsPerStudentDay());
    }

    private void printTimetable(ProblemInstance instance, Map<String, String> slotOfCourse) {
        Map<String, List<String>> bySlot = new HashMap<>();
        for (int c = 0; c < instance.courseCount(); c++) {
            String code = instance.course(c).code();
            bySlot.computeIfAbsent(slotOfCourse.get(code), k -> new ArrayList<>())
                    .add(code + "(" + instance.studentsOf(c).length + ")");
        }
        for (Slot slot : instance.slots()) {
            List<String> exams = bySlot.getOrDefault(slot.id(), List.of());
            out.printf("%-10s %-9s %s%n", slot.date(), slot.session(), exams.isEmpty() ? "-" : String.join(" ", exams));
        }
    }

    private static ProblemInstance load(Map<String, String> options) throws IOException {
        int days = Integer.parseInt(options.getOrDefault("days", "10"));
        int sessions = Integer.parseInt(options.getOrDefault("sessions", "2"));
        LocalDate start = LocalDate.parse(options.getOrDefault("start", "2026-12-01"));
        long seed = Long.parseLong(options.getOrDefault("seed", "7"));
        SolverParams.Builder params = SolverParams.builder().seed(seed);
        if (options.containsKey("optimizer")) {
            params.optimizer(OptimizerType.valueOf(options.get("optimizer").toUpperCase(java.util.Locale.ROOT)));
        }
        if (options.containsKey("iterations")) {
            params.maxIterations(Long.parseLong(options.get("iterations")));
        }
        if (options.containsKey("time-limit")) {
            params.timeLimitMillis(Long.parseLong(options.get("time-limit")) * 1000);
        }

        if (options.containsKey("synthetic")) {
            String[] parts = options.get("synthetic").split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("--synthetic expects COURSES:STUDENTS, e.g. 180:2400");
            }
            return SyntheticInstanceGenerator.builder(new SyntheticInstanceGenerator.Spec(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), days, sessions, start, seed))
                    .params(params.build())
                    .build();
        }

        ProblemInstance.Builder builder = ProblemInstance.builder().params(params.build());
        Set<String> courses = new HashSet<>();
        if (options.containsKey("courses")) {
            courses.addAll(InstanceCsvLoader.loadCourses(Path.of(options.get("courses")), builder));
        }
        InstanceCsvLoader.loadEnrolments(Path.of(options.get("enrolments")), builder, courses);
        if (options.containsKey("rooms")) {
            InstanceCsvLoader.loadRooms(Path.of(options.get("rooms")), builder);
        }
        if (options.containsKey("faculty")) {
            InstanceCsvLoader.loadFaculty(Path.of(options.get("faculty")), builder);
        }
        builder.slots(SyntheticInstanceGenerator.slots(
                new SyntheticInstanceGenerator.Spec(4, 1, days, sessions, start, seed)));
        return builder.build();
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (key.equals("help")) {
                options.put(key, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + arg);
            }
            options.put(key, args[++i]);
        }
        return options;
    }

    private void usage() {
        out.println("""
                Usage:
                  examgrid-solver --enrolments FILE [--courses FILE] [--rooms FILE] [--faculty FILE]
                                  [--days 10] [--sessions 2] [--start 2026-12-01]
                  examgrid-solver --synthetic COURSES:STUDENTS [--days 10] [--sessions 2] [--seed 7]

                Optimiser options:
                  --optimizer annealing|tabu|none   --iterations 300000   --time-limit SECONDS
                  --out DIR    write timetable.csv, seats.csv and invigilation.csv

                CSV formats (';' separates list values):
                  enrolments.csv  roll,course
                  courses.csv     code,name,hard,sessions
                  rooms.csv       code,building,rows,columns,blocked
                  faculty.csv     id,name,teaches,unavailable""");
    }
}
