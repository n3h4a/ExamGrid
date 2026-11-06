package com.examgrid.solver.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SolverCliTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final SolverCli cli = new SolverCli(new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

    @Test
    void solvesASyntheticInstance() {
        assertThat(cli.run(new String[] {"--synthetic", "60:400"})).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Clash-free timetable");
    }

    @Test
    void explainsInfeasibleInput() {
        assertThat(cli.run(new String[] {"--synthetic", "60:400", "--days", "1", "--sessions", "1"})).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("No clash-free timetable exists");
    }

    @Test
    void printsUsageForMissingInput() {
        assertThat(cli.run(new String[0])).isEqualTo(2);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("Usage");
    }

    @Test
    void rejectsDanglingOption() {
        assertThat(cli.run(new String[] {"--synthetic"})).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("missing value");
    }
}
