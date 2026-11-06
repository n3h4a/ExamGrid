package com.examgrid.solver.optimize;

import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.ProblemInstance;

import java.util.Arrays;

/**
 * Student-level quality report of a timetable, computed directly from enrolments rather than
 * from the conflict graph, so it doubles as an independent check of the optimiser's score.
 *
 * @param clashPairs            exam pairs a student sits in the same slot (must be 0)
 * @param sameDayPairs          exam pairs a student sits on the same day
 * @param studentsWithSameDay   distinct students with two or more exams on one day
 * @param backToBackPairs       exam pairs in consecutive slots on consecutive days
 * @param studentsWithBackToBack distinct students with at least one back-to-back pair
 * @param hardGapPairs          hard-paper pairs closer than the minimum gap
 * @param score                 weighted soft-constraint score
 * @param slotsUsed             slots holding at least one exam
 * @param maxExamsPerStudentDay most exams any student sits on one day
 */
public record TimetableMetrics(long clashPairs, long sameDayPairs, long studentsWithSameDay, long backToBackPairs,
                               long studentsWithBackToBack, long hardGapPairs, long score, int slotsUsed,
                               int maxExamsPerStudentDay) {

    public static TimetableMetrics of(ProblemInstance instance, ExamGroups groups, PenaltyModel penalties,
                                      int[] slotOfNode) {
        long clash = 0;
        long sameDay = 0;
        long studentsSameDay = 0;
        long b2b = 0;
        long studentsB2b = 0;
        long hardGap = 0;
        int maxPerDay = 0;
        boolean[] used = new boolean[instance.slotCount()];
        for (int c = 0; c < instance.courseCount(); c++) {
            if (instance.studentsOf(c).length > 0) {
                used[slotOfNode[groups.nodeOf(c)]] = true;
            }
        }
        for (int s = 0; s < instance.studentCount(); s++) {
            int[] courses = instance.coursesOf(s);
            int k = courses.length;
            int[] slots = new int[k];
            boolean[] hard = new boolean[k];
            for (int i = 0; i < k; i++) {
                slots[i] = slotOfNode[groups.nodeOf(courses[i])];
                hard[i] = instance.course(courses[i]).hard();
            }
            boolean anySameDay = false;
            boolean anyB2b = false;
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    if (slots[i] == slots[j]) {
                        clash++;
                        continue;
                    }
                    if (penalties.sameDay(slots[i], slots[j])) {
                        sameDay++;
                        anySameDay = true;
                    } else if (penalties.backToBack(slots[i], slots[j])) {
                        b2b++;
                        anyB2b = true;
                    }
                    if (hard[i] && hard[j] && penalties.hardGap(slots[i], slots[j])) {
                        hardGap++;
                    }
                }
            }
            if (anySameDay) {
                studentsSameDay++;
            }
            if (anyB2b) {
                studentsB2b++;
            }
            maxPerDay = Math.max(maxPerDay, maxPerDay(instance, slots));
        }
        long score = sameDay * penalties.weights().sameDay()
                + b2b * penalties.weights().backToBack()
                + hardGap * penalties.weights().hardGap();
        int slotsUsed = 0;
        for (boolean u : used) {
            slotsUsed += u ? 1 : 0;
        }
        return new TimetableMetrics(clash, sameDay, studentsSameDay, b2b, studentsB2b, hardGap, score, slotsUsed,
                maxPerDay);
    }

    private static int maxPerDay(ProblemInstance instance, int[] slots) {
        if (slots.length == 0) {
            return 0;
        }
        String[] dates = Arrays.stream(slots).mapToObj(s -> instance.slot(s).date().toString()).sorted()
                .toArray(String[]::new);
        int best = 1;
        int run = 1;
        for (int i = 1; i < dates.length; i++) {
            run = dates[i].equals(dates[i - 1]) ? run + 1 : 1;
            best = Math.max(best, run);
        }
        return best;
    }
}
