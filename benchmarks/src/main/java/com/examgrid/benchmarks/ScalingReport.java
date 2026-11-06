package com.examgrid.benchmarks;

import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.pipeline.ExamGridSolver;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionMetrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Runs the full pipeline on synthetic universities of increasing size and writes a Markdown
 * table and a CSV with the median of several timed runs (after warm-up).
 *
 * <pre>
 * java -cp benchmarks/target/examgrid-benchmarks.jar com.examgrid.benchmarks.ScalingReport docs/benchmarks
 * </pre>
 */
public final class ScalingReport {

    private static final int[] SIZES = {50, 100, 200, 500};
    private static final int WARMUP = 2;
    private static final int RUNS = 5;

    private ScalingReport() {
    }

    static ProblemInstance instance(int courses) {
        return SolverBenchmarks.syntheticInstance(courses, 7L);
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/benchmarks");
        Files.createDirectories(out);
        List<String> csv = new ArrayList<>();
        csv.add("courses,students,enrolments,slots,rooms,faculty,median_ms,dsatur_score,final_score,"
                + "same_day_pairs,back_to_back_pairs,room_slots,seat_utilisation,duty_spread");
        StringBuilder md = new StringBuilder();
        md.append("| Courses | Students | Enrolments | Slots | Rooms | Median solve | DSatur score | Final score "
                + "| Same-day pairs | Back-to-back pairs | Seat use | Duty spread |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int courses : SIZES) {
            ProblemInstance instance = instance(courses).withParams(
                    SolverParams.builder().maxIterations(300_000).timeLimitMillis(60_000).build());
            for (int i = 0; i < WARMUP; i++) {
                new ExamGridSolver().solve(instance);
            }
            List<Long> times = new ArrayList<>();
            Solution last = null;
            for (int i = 0; i < RUNS; i++) {
                long t = System.nanoTime();
                last = new ExamGridSolver().solve(instance);
                times.add((System.nanoTime() - t) / 1_000_000);
            }
            Collections.sort(times);
            long median = times.get(RUNS / 2);
            SolutionMetrics m = last.metrics();
            String spread = m.minFacultyDuties() + "–" + m.maxFacultyDuties();
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%s", instance.courseCount(),
                    instance.studentCount(), instance.enrolmentCount(), instance.slotCount(), instance.roomCount(),
                    instance.faculty().size(), median, m.initialScore(), m.score(), m.timetable().sameDayPairs(),
                    m.timetable().backToBackPairs(), m.roomSlotsUsed(), m.seatUtilisation(), spread));
            md.append(String.format(Locale.ROOT, "| %d | %,d | %,d | %d | %d | %,d ms | %,d | %,d | %d | %d | %.0f %% | %s |%n",
                    instance.courseCount(), instance.studentCount(), instance.enrolmentCount(), instance.slotCount(),
                    instance.roomCount(), median, m.initialScore(), m.score(), m.timetable().sameDayPairs(),
                    m.timetable().backToBackPairs(), 100 * m.seatUtilisation(), spread));
            System.out.printf("%d courses: %d ms (score %d -> %d)%n", instance.courseCount(), median, m.initialScore(),
                    m.score());
        }
        Files.write(out.resolve("scaling.csv"), csv);
        Files.writeString(out.resolve("scaling.md"), md.toString());
        System.out.println("Wrote " + out.resolve("scaling.md"));
    }
}
