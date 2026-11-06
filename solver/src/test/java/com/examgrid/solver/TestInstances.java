package com.examgrid.solver;

import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Shared fixtures for solver tests. */
public final class TestInstances {

    public static final LocalDate START = LocalDate.of(2026, 12, 1);

    private TestInstances() {
    }

    /** {@code days × sessions} slots starting on {@link #START}, consecutive calendar days. */
    public static List<Slot> slots(int days, int sessionsPerDay) {
        List<Slot> slots = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            for (int s = 0; s < sessionsPerDay; s++) {
                slots.add(Slot.of(START.plusDays(d), Session.values()[s]));
            }
        }
        return slots;
    }

    /**
     * The worked example from the specification: Asha → CS301, MA201; Ravi → CS301, PH101;
     * Meena → MA201, PH101; Kiran → EE210.
     */
    public static ProblemInstance.Builder specExample(int slots) {
        return ProblemInstance.builder()
                .course(Course.of("CS301"))
                .course(Course.of("MA201"))
                .course(Course.of("PH101"))
                .course(Course.of("EE210"))
                .enrol("ASHA", "CS301").enrol("ASHA", "MA201")
                .enrol("RAVI", "CS301").enrol("RAVI", "PH101")
                .enrol("MEENA", "MA201").enrol("MEENA", "PH101")
                .enrol("KIRAN", "EE210")
                .slots(slots(slots, 1));
    }

    /**
     * Instance whose conflict graph is exactly the given edge list over courses C0..C(n-1):
     * one student per edge, plus one solo student per course so every course has candidates.
     */
    public static ProblemInstance.Builder fromEdges(int n, int[][] edges, int slotCount) {
        ProblemInstance.Builder b = ProblemInstance.builder().slots(slots(slotCount, 1));
        for (int i = 0; i < n; i++) {
            b.course(Course.of(course(i)));
            b.enrol("SOLO" + i, course(i));
        }
        for (int e = 0; e < edges.length; e++) {
            b.enrol("EDGE" + e, course(edges[e][0]));
            b.enrol("EDGE" + e, course(edges[e][1]));
        }
        return b;
    }

    public static String course(int i) {
        return "C" + i;
    }

    public static int[][] cycle(int n) {
        int[][] edges = new int[n][];
        for (int i = 0; i < n; i++) {
            edges[i] = new int[] {i, (i + 1) % n};
        }
        return edges;
    }

    /** Wheel: hub 0 joined to a rim cycle 1..rim. */
    public static int[][] wheel(int rim) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 1; i <= rim; i++) {
            edges.add(new int[] {0, i});
            edges.add(new int[] {i, i % rim + 1});
        }
        return edges.toArray(int[][]::new);
    }

    public static int[][] complete(int n) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                edges.add(new int[] {i, j});
            }
        }
        return edges.toArray(int[][]::new);
    }

    public static int[][] completeBipartite(int left, int right) {
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < left; i++) {
            for (int j = 0; j < right; j++) {
                edges.add(new int[] {i, left + j});
            }
        }
        return edges.toArray(int[][]::new);
    }
}
