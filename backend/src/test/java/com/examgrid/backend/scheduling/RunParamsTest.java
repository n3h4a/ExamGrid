package com.examgrid.backend.scheduling;

import com.examgrid.solver.model.OptimizerType;
import com.examgrid.solver.model.SolverParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunParamsTest {

    @Test
    void emptyRequestResolvesToSolverDefaults() {
        RunParams resolved = new RunParams(null, null, null, null, null, null, null, null, null).resolved();
        SolverParams defaults = SolverParams.defaults();
        assertThat(resolved.optimizer()).isEqualTo(defaults.optimizer());
        assertThat(resolved.weights().sameDay()).isEqualTo(defaults.weights().sameDay());
        assertThat(resolved.timeLimitSeconds()).isEqualTo(RunParams.DEFAULT_TIME_LIMIT_SECONDS);
        assertThat(resolved.diagonalAdjacency()).isFalse();
    }

    @Test
    void givenValuesWinAndMapOntoSolverParams() {
        RunParams request = new RunParams(" exam week ", OptimizerType.TABU, 5_000L, 7, 99L,
                new RunParams.Weights(20, null, 0, 3), 25, 2, true);
        SolverParams p = request.toSolverParams();
        assertThat(request.resolved().label()).isEqualTo("exam week");
        assertThat(p.optimizer()).isEqualTo(OptimizerType.TABU);
        assertThat(p.maxIterations()).isEqualTo(5_000);
        assertThat(p.timeLimitMillis()).isEqualTo(7_000);
        assertThat(p.seed()).isEqualTo(99);
        assertThat(p.weights().sameDay()).isEqualTo(20);
        assertThat(p.weights().backToBack()).isEqualTo(SolverParams.defaults().weights().backToBack());
        assertThat(p.weights().hardGap()).isZero();
        assertThat(p.weights().hardMinGapDays()).isEqualTo(3);
        assertThat(p.studentsPerInvigilator()).isEqualTo(25);
        assertThat(p.minInvigilatorsPerRoom()).isEqualTo(2);
        assertThat(p.diagonalAdjacency()).isTrue();
    }
}
