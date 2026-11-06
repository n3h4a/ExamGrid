package com.examgrid.solver.coloring;

import com.examgrid.solver.coloring.InfeasibilityIssue.Type;
import com.examgrid.solver.graph.CliqueFinder;
import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.util.BipartiteMatching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explains why an instance cannot be timetabled. Checks run from the cheapest and most
 * specific to the most general, and every check that produces an issue marked {@code proof}
 * is a certificate that no clash-free timetable exists, not just a heuristic failure.
 */
public final class InfeasibilityAnalyzer {

    private static final int MAX_ISSUES_PER_TYPE = 10;
    private static final int MAX_WITNESSES = 5;

    private final ProblemInstance instance;
    private final ConflictGraph graph;
    private final ExamGroups groups;
    private final ColoringProblem problem;

    public InfeasibilityAnalyzer(ProblemInstance instance, ConflictGraph graph, ColoringProblem problem) {
        this.instance = instance;
        this.graph = graph;
        this.groups = graph.groups();
        this.problem = problem;
    }

    /** Checks that need no search; an empty result means the instance is worth solving. */
    public List<InfeasibilityIssue> structuralIssues() {
        List<InfeasibilityIssue> issues = new ArrayList<>();
        sameSlotGroupConflicts(issues);
        emptyDomains(issues);
        lockConflicts(issues);
        oversizedPapers(issues);
        totalCapacity(issues);
        studentOverload(issues);
        cliqueBound(issues);
        return issues;
    }

    /** Full report after the search failed to place {@code uncolored}. */
    public InfeasibilityReport analyse(List<Integer> uncolored) {
        List<InfeasibilityIssue> issues = structuralIssues();
        if (issues.isEmpty()) {
            issues.add(unresolved(uncolored));
        }
        return new InfeasibilityReport(issues);
    }

    private void sameSlotGroupConflicts(List<InfeasibilityIssue> issues) {
        int count = 0;
        for (ConflictGraph.InternalConflict ic : graph.internalConflicts()) {
            if (count++ == MAX_ISSUES_PER_TYPE) {
                break;
            }
            String a = code(ic.courseA());
            String b = code(ic.courseB());
            String roll = instance.student(ic.student());
            issues.add(new InfeasibilityIssue(Type.SAME_SLOT_GROUP_CONFLICT,
                    a + " and " + b + " must be held in the same slot, but student " + roll + " sits both.",
                    List.of(a, b), List.of(roll), true));
        }
    }

    private void emptyDomains(List<InfeasibilityIssue> issues) {
        int count = 0;
        for (int v = 0; v < graph.nodeCount() && count < MAX_ISSUES_PER_TYPE; v++) {
            if (problem.domainSize(v) == 0) {
                count++;
                issues.add(new InfeasibilityIssue(Type.EMPTY_DOMAIN,
                        "No slot satisfies the session rules and locks of " + label(v) + ".",
                        codes(v), List.of(), true));
            }
        }
    }

    private void lockConflicts(List<InfeasibilityIssue> issues) {
        int count = 0;
        for (int v = 0; v < graph.nodeCount() && count < MAX_ISSUES_PER_TYPE; v++) {
            if (!problem.isLocked(v)) {
                continue;
            }
            for (int u : graph.neighbours(v)) {
                if (u > v && problem.isLocked(u) && problem.lockedSlot(u) == problem.lockedSlot(v)) {
                    count++;
                    List<String> witnesses = witnesses(u, v);
                    issues.add(new InfeasibilityIssue(Type.LOCK_CONFLICT,
                            label(v) + " and " + label(u) + " are both locked to "
                                    + instance.slot(problem.lockedSlot(v)).id() + " but share "
                                    + graph.weight(u, v) + " student(s), e.g. " + String.join(", ", witnesses) + ".",
                            concat(codes(v), codes(u)), witnesses, true));
                }
            }
        }
    }

    private void oversizedPapers(List<InfeasibilityIssue> issues) {
        if (instance.roomCount() == 0) {
            return;
        }
        boolean diagonal = instance.params().diagonalAdjacency();
        int seats = instance.totalSeats();
        int samePaperSeats = instance.rooms().stream().mapToInt(r -> r.maxSamePaper(diagonal)).sum();
        int count = 0;
        for (int v = 0; v < graph.nodeCount() && count < MAX_ISSUES_PER_TYPE; v++) {
            if (graph.nodeSize(v) > seats) {
                count++;
                issues.add(new InfeasibilityIssue(Type.PAPER_TOO_LARGE,
                        label(v) + " has " + graph.nodeSize(v) + " candidates but all rooms together seat only "
                                + seats + ".", codes(v), List.of(), true));
            }
        }
        for (int c = 0; c < instance.courseCount() && count < MAX_ISSUES_PER_TYPE; c++) {
            int size = instance.studentsOf(c).length;
            if (size > samePaperSeats && size <= seats) {
                count++;
                issues.add(new InfeasibilityIssue(Type.PAPER_TOO_LARGE,
                        code(c) + " has " + size + " candidates, but without seating two of them side by side "
                                + "the rooms hold at most " + samePaperSeats + ".", List.of(code(c)), List.of(), true));
            }
        }
    }

    private void totalCapacity(List<InfeasibilityIssue> issues) {
        if (instance.roomCount() == 0) {
            return;
        }
        long needed = instance.enrolmentCount();
        long available = 0;
        for (int s = 0; s < problem.slotCount(); s++) {
            available += problem.slotCapacity(s);
        }
        if (needed > available) {
            issues.add(new InfeasibilityIssue(Type.CAPACITY_EXCEEDED,
                    needed + " exam seats are needed but " + problem.slotCount() + " slots × "
                            + instance.totalSeats() + " seats provide only " + available + ".",
                    List.of(), List.of(), true));
        }
    }

    /**
     * Hall's condition per student: their exams need pairwise distinct slots, each from its
     * own domain. If the maximum matching of exams to slots is smaller than the number of
     * exams, that student alone makes the instance infeasible.
     */
    private void studentOverload(List<InfeasibilityIssue> issues) {
        Set<String> seenNodeSets = new HashSet<>();
        int count = 0;
        for (int s = 0; s < instance.studentCount() && count < MAX_ISSUES_PER_TYPE; s++) {
            int[] nodes = Arrays.stream(instance.coursesOf(s)).map(groups::nodeOf).distinct().sorted().toArray();
            if (nodes.length < 2 || !seenNodeSets.add(Arrays.toString(nodes))) {
                continue;
            }
            boolean[][] allowed = new boolean[nodes.length][problem.slotCount()];
            for (int i = 0; i < nodes.length; i++) {
                for (int t = 0; t < problem.slotCount(); t++) {
                    allowed[i][t] = problem.allowed(nodes[i], t);
                }
            }
            int matched = new BipartiteMatching(allowed).maximumMatching();
            if (matched < nodes.length) {
                count++;
                List<String> courses = new ArrayList<>();
                for (int v : nodes) {
                    courses.addAll(codes(v));
                }
                String roll = instance.student(s);
                String why = nodes.length > problem.slotCount()
                        ? "student " + roll + " sits " + nodes.length + " exams but there are only "
                                + problem.slotCount() + " slots."
                        : "student " + roll + " sits " + nodes.length + " exams, but their session rules and "
                                + "locks leave only " + matched + " distinct slots for them.";
                issues.add(new InfeasibilityIssue(Type.STUDENT_OVERLOAD, capitalise(why), courses, List.of(roll),
                        true));
            }
        }
    }

    private void cliqueBound(List<InfeasibilityIssue> issues) {
        int[] clique = CliqueFinder.largestClique(graph);
        if (clique.length < 2) {
            return;
        }
        boolean[][] allowed = new boolean[clique.length][problem.slotCount()];
        for (int i = 0; i < clique.length; i++) {
            for (int t = 0; t < problem.slotCount(); t++) {
                allowed[i][t] = problem.allowed(clique[i], t);
            }
        }
        int matched = new BipartiteMatching(allowed).maximumMatching();
        if (matched >= clique.length) {
            return;
        }
        List<String> courses = new ArrayList<>();
        Set<String> students = new LinkedHashSet<>();
        for (int i = 0; i < clique.length; i++) {
            courses.addAll(codes(clique[i]));
            if (students.size() < MAX_WITNESSES * 2) {
                students.addAll(witnesses(clique[i], clique[(i + 1) % clique.length]));
            }
        }
        issues.add(new InfeasibilityIssue(Type.CLIQUE_TOO_LARGE,
                clique.length + " exams (" + String.join(", ", courses) + ") all share students with each other, "
                        + "so they need " + clique.length + " different slots, but only " + matched
                        + " are usable for them.", courses, List.copyOf(students), true));
    }

    private InfeasibilityIssue unresolved(List<Integer> uncolored) {
        List<String> courses = new ArrayList<>();
        Set<String> students = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder("Search could not place ");
        int shown = 0;
        for (int v : uncolored) {
            courses.addAll(codes(v));
            if (shown++ < MAX_WITNESSES) {
                sb.append(shown > 1 ? ", " : "").append(label(v))
                        .append(" (conflicts with ").append(graph.degree(v)).append(" exams)");
                int[] nb = graph.neighbours(v);
                if (nb.length > 0) {
                    students.addAll(witnesses(v, nb[0]));
                }
            }
        }
        if (uncolored.size() > MAX_WITNESSES) {
            sb.append(" and ").append(uncolored.size() - MAX_WITNESSES).append(" more");
        }
        sb.append(". Add slots, relax session rules or remove locks.");
        return new InfeasibilityIssue(Type.UNRESOLVED, sb.toString(), courses, List.copyOf(students), false);
    }

    private List<String> witnesses(int a, int b) {
        return Arrays.stream(ConflictGraph.sharedStudents(instance, groups, a, b, MAX_WITNESSES))
                .mapToObj(instance::student)
                .toList();
    }

    private String code(int course) {
        return instance.course(course).code();
    }

    private List<String> codes(int node) {
        return Arrays.stream(groups.coursesOf(node)).mapToObj(this::code).toList();
    }

    private String label(int node) {
        return String.join("+", codes(node));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
