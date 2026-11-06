package com.examgrid.solver.optimize;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.coloring.ColoringProblem;
import com.examgrid.solver.coloring.ColoringResult;
import com.examgrid.solver.coloring.DSaturColoring;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

final class OptimizeFixtures {

    private OptimizeFixtures() {
    }

    /** Random instance with slack: many slots, few exams per student, optional locks and labs. */
    static ProblemInstance randomInstance(long seed, int courses, int students, int days) {
        SplittableRandom rnd = new SplittableRandom(seed);
        List<Slot> slots = TestInstances.slots(days, 2);
        ProblemInstance.Builder b = ProblemInstance.builder().slots(slots);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < courses; i++) {
            Set<Session> sessions = rnd.nextInt(6) == 0 ? Set.of(Session.AFTERNOON) : Set.of();
            b.course(new Course("C" + i, "", rnd.nextInt(3) == 0, sessions));
            codes.add("C" + i);
        }
        for (int s = 0; s < students; s++) {
            int load = 1 + rnd.nextInt(Math.min(4, courses));
            for (int j = 0; j < load; j++) {
                b.enrol("S" + s, codes.get(rnd.nextInt(courses)));
            }
        }
        if (rnd.nextBoolean()) {
            b.lock(codes.get(rnd.nextInt(courses)), slots.get(1 + 2 * rnd.nextInt(days)).id());
        }
        return b.build();
    }

    /** Builds a DSatur-initialised timetable, or returns null if DSatur cannot colour it. */
    static Timetable timetable(ProblemInstance instance) {
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        ColoringProblem p = ColoringProblem.from(instance, g);
        ColoringResult r = new DSaturColoring().color(p);
        if (!r.complete()) {
            return null;
        }
        return new Timetable(p, new PenaltyModel(instance.slots(), instance.params().weights()), r.slotOf());
    }
}
