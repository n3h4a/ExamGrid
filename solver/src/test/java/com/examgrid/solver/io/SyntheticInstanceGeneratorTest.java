package com.examgrid.solver.io;

import com.examgrid.solver.model.ProblemInstance;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticInstanceGeneratorTest {

    @Test
    void isDeterministicAndRoughlyTheRequestedSize() {
        SyntheticInstanceGenerator.Spec spec = SyntheticInstanceGenerator.Spec.of(180, 2400);
        ProblemInstance a = SyntheticInstanceGenerator.generate(spec);
        ProblemInstance b = SyntheticInstanceGenerator.generate(spec);
        assertThat(a.courseCount()).isBetween(150, 200);
        assertThat(a.studentCount()).isEqualTo(2400);
        assertThat(a.slotCount()).isEqualTo(20);
        assertThat(a.enrolmentCount()).isEqualTo(b.enrolmentCount());
        assertThat(a.students()).isEqualTo(b.students());
        assertThat(a.totalSeats()).isGreaterThan(0);
        assertThat(a.faculty()).isNotEmpty();
    }

    @Test
    void skipsSundays() {
        assertThat(SyntheticInstanceGenerator.slots(SyntheticInstanceGenerator.Spec.of(40, 10)))
                .noneMatch(s -> s.date().getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    @Test
    void rollNumbersFollowTheYearDepartmentSerialPattern() {
        ProblemInstance instance = SyntheticInstanceGenerator.generate(SyntheticInstanceGenerator.Spec.of(48, 100));
        assertThat(instance.students()).allMatch(r -> r.matches("\\d{2}[A-Z]{2}\\d{3}"));
    }
}
