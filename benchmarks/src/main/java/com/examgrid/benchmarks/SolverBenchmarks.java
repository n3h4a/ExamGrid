package com.examgrid.benchmarks;

import com.examgrid.solver.coloring.ColoringProblem;
import com.examgrid.solver.coloring.ColoringResult;
import com.examgrid.solver.coloring.DSaturColoring;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.io.SyntheticInstanceGenerator;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.pipeline.ExamGridSolver;
import com.examgrid.solver.pipeline.Solution;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks of the solver stages on synthetic universities.
 *
 * <pre>
 * mvn -pl solver,benchmarks package -DskipTests
 * java -jar benchmarks/target/examgrid-benchmarks.jar -f 1 -wi 3 -i 5
 * </pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class SolverBenchmarks {

    @Param({"50", "100", "200", "500"})
    public int courses;

    private ProblemInstance instance;
    private ExamGroups groups;
    private ColoringProblem coloring;

    @Setup
    public void setUp() {
        instance = ScalingReport.instance(courses).withParams(
                SolverParams.builder().maxIterations(300_000).timeLimitMillis(60_000).build());
        groups = ExamGroups.of(instance);
        coloring = ColoringProblem.from(instance, ConflictGraph.build(instance, groups));
    }

    @Benchmark
    public ConflictGraph conflictGraph() {
        return ConflictGraph.build(instance, groups);
    }

    @Benchmark
    public ColoringResult dsatur() {
        return new DSaturColoring().color(coloring);
    }

    @Benchmark
    public Solution fullPipeline() {
        return new ExamGridSolver().solve(instance);
    }

    static ProblemInstance syntheticInstance(int courses, long seed) {
        return SyntheticInstanceGenerator.generate(new SyntheticInstanceGenerator.Spec(courses, courses * 13, 12, 2,
                LocalDate.of(2026, 12, 1), seed));
    }
}
