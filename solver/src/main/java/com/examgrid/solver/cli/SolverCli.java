package com.examgrid.solver.cli;

import com.examgrid.solver.coloring.ColoringProblem;
import com.examgrid.solver.coloring.InfeasibilityAnalyzer;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.coloring.InitialTimetable;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.io.InstanceCsvLoader;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Slot;

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
 * Command-line entry point: CSV (or synthetic data) in, timetable out.
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
            return solve(instance);
        } catch (IllegalArgumentException e) {
            err.println("Invalid input: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            err.println("Could not read input: " + e.getMessage());
            return 2;
        }
    }

    private int solve(ProblemInstance instance) {
        long started = System.nanoTime();
        ExamGroups groups = ExamGroups.of(instance);
        ConflictGraph graph = ConflictGraph.build(instance, groups);
        ColoringProblem problem = ColoringProblem.from(instance, graph);
        out.printf("Loaded %d courses, %d students, %d enrolments, %d slots, %d rooms%n",
                instance.courseCount(), instance.studentCount(), instance.enrolmentCount(),
                instance.slotCount(), instance.roomCount());
        out.printf("Conflict graph: %d exam nodes, %d edges, max degree %d%n",
                graph.nodeCount(), graph.edgeCount(), graph.maxDegree());
        InitialTimetable.Result result;
        try {
            result = new InitialTimetable(20_000, instance.params().seed())
                    .build(new InfeasibilityAnalyzer(instance, graph, problem), problem);
        } catch (InfeasibleException e) {
            err.println(e.report().summary());
            return 1;
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        out.printf("Clash-free timetable using %d of %d slots%s in %d ms%n%n", result.colorsUsed(),
                instance.slotCount(), result.repaired() ? " (TabuCol repair needed)" : "", elapsedMs);
        printTimetable(instance, groups, result.slotOf());
        return 0;
    }

    private void printTimetable(ProblemInstance instance, ExamGroups groups, int[] slotOfNode) {
        List<List<String>> bySlot = new ArrayList<>();
        for (int s = 0; s < instance.slotCount(); s++) {
            bySlot.add(new ArrayList<>());
        }
        for (int c = 0; c < instance.courseCount(); c++) {
            bySlot.get(slotOfNode[groups.nodeOf(c)]).add(instance.course(c).code()
                    + "(" + instance.studentsOf(c).length + ")");
        }
        for (int s = 0; s < instance.slotCount(); s++) {
            Slot slot = instance.slot(s);
            out.printf("%-10s %-9s %s%n", slot.date(), slot.session(),
                    bySlot.get(s).isEmpty() ? "-" : String.join(" ", bySlot.get(s)));
        }
    }

    private static ProblemInstance load(Map<String, String> options) throws IOException {
        int days = Integer.parseInt(options.getOrDefault("days", "10"));
        int sessions = Integer.parseInt(options.getOrDefault("sessions", "2"));
        LocalDate start = LocalDate.parse(options.getOrDefault("start", "2026-12-01"));
        long seed = Long.parseLong(options.getOrDefault("seed", "7"));

        if (options.containsKey("synthetic")) {
            String[] parts = options.get("synthetic").split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("--synthetic expects COURSES:STUDENTS, e.g. 180:2400");
            }
            return SyntheticInstanceGenerator.generate(new SyntheticInstanceGenerator.Spec(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), days, sessions, start, seed));
        }

        ProblemInstance.Builder builder = ProblemInstance.builder();
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

                CSV formats (';' separates list values):
                  enrolments.csv  roll,course
                  courses.csv     code,name,hard,sessions
                  rooms.csv       code,building,rows,columns,blocked
                  faculty.csv     id,name,teaches,unavailable""");
    }
}
