package com.examgrid.solver.invigilation;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InvigilationAssignerTest {

    @Test
    void loadsDifferByAtMostOneWithoutConstraints() {
        ProblemInstance instance = instance(7, Set.of(), Set.of());
        List<Duty> duties = new ArrayList<>();
        for (int s = 0; s < 6; s++) {
            for (int r = 0; r < 3; r++) {
                duties.add(new Duty(s, r, Set.of(0), 1));
            }
        }
        InvigilationAssigner.Result result = new InvigilationAssigner(instance).assign(duties);
        assertThat(result.unfilled()).isZero();
        assertThat(result.assignments()).hasSize(18);
        int[] load = result.load();
        assertThat(Arrays.stream(load).max().getAsInt() - Arrays.stream(load).min().getAsInt()).isLessThanOrEqualTo(1);
        assertNoDoubleBooking(duties, result);
    }

    @Test
    void neverAssignsTheTeacherOfAPaperInTheRoom() {
        ProblemInstance instance = instance(3, Set.of("C0"), Set.of());
        List<Duty> duties = List.of(new Duty(0, 0, Set.of(0), 1), new Duty(1, 0, Set.of(0), 1));
        InvigilationAssigner.Result result = new InvigilationAssigner(instance).assign(duties);
        assertThat(result.assignments()).extracting(a -> a[1]).doesNotContain(0);
    }

    @Test
    void skipsFacultyAwayForTheSlot() {
        String away = TestInstances.slots(6, 1).get(2).id();
        ProblemInstance instance = instance(2, Set.of(), Set.of(away));
        List<Duty> duties = List.of(new Duty(2, 0, Set.of(0), 1));
        assertThat(new InvigilationAssigner(instance).assign(duties).assignments().getFirst()[1]).isEqualTo(1);
    }

    @Test
    void reportsUnfilledPositions() {
        ProblemInstance instance = instance(2, Set.of(), Set.of());
        List<Duty> duties = List.of(new Duty(0, 0, Set.of(0), 3));
        InvigilationAssigner.Result result = new InvigilationAssigner(instance).assign(duties);
        assertThat(result.assignments()).hasSize(2);
        assertThat(result.unfilled()).isEqualTo(1);
    }

    @Test
    void requiredRoundsUp() {
        assertThat(InvigilationAssigner.required(61, 30, 1)).isEqualTo(3);
        assertThat(InvigilationAssigner.required(5, 30, 2)).isEqualTo(2);
    }

    /** Faculty F0..F(n-1); F0 teaches {@code f0Teaches} and is away for {@code f0Away}. */
    private static ProblemInstance instance(int faculty, Set<String> f0Teaches, Set<String> f0Away) {
        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(6, 1))
                .course(Course.of("C0")).enrol("S", "C0")
                .room(Room.grid("R0", 2, 2)).room(Room.grid("R1", 2, 2)).room(Room.grid("R2", 2, 2));
        for (int i = 0; i < faculty; i++) {
            b.faculty(i == 0 ? new Faculty("F0", "", f0Teaches, f0Away) : Faculty.of("F" + i));
        }
        return b.build();
    }

    private static void assertNoDoubleBooking(List<Duty> duties, InvigilationAssigner.Result result) {
        Set<String> booked = new HashSet<>();
        for (int[] a : result.assignments()) {
            assertThat(booked.add(a[1] + "@" + duties.get(a[0]).slot())).isTrue();
        }
    }
}
