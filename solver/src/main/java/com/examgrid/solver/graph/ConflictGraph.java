package com.examgrid.solver.graph;

import com.examgrid.solver.model.ProblemInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted, undirected conflict graph over exam nodes. Two nodes are adjacent when at least
 * one student sits both; the edge weight is the number of such students, which is exactly
 * what the soft-constraint score needs. A second weight counts students who sit a
 * <em>hard</em> paper on both sides of the edge.
 *
 * <p>Adjacency lists are sorted, so {@link #weight(int, int)} is a binary search.
 */
public final class ConflictGraph {

    private final ExamGroups groups;
    private final int[][] neighbours;
    private final int[][] weights;
    private final int[][] hardWeights;
    private final int[] nodeSize;
    private final int edgeCount;
    private final List<InternalConflict> internalConflicts;

    /** A student who sits two courses of one same-slot group, which can never be scheduled. */
    public record InternalConflict(int node, int student, int courseA, int courseB) {
    }

    private ConflictGraph(ExamGroups groups, int[][] neighbours, int[][] weights, int[][] hardWeights,
                          int[] nodeSize, int edgeCount, List<InternalConflict> internalConflicts) {
        this.groups = groups;
        this.neighbours = neighbours;
        this.weights = weights;
        this.hardWeights = hardWeights;
        this.nodeSize = nodeSize;
        this.edgeCount = edgeCount;
        this.internalConflicts = List.copyOf(internalConflicts);
    }

    /**
     * Builds the graph by visiting each student once and connecting every pair of nodes they
     * sit: O(Σ k²) for k exams per student, independent of the number of courses.
     */
    public static ConflictGraph build(ProblemInstance instance, ExamGroups groups) {
        int n = groups.nodeCount();
        Map<Long, int[]> edges = new HashMap<>();
        List<InternalConflict> internal = new ArrayList<>();
        int[] size = new int[n];
        for (int c = 0; c < instance.courseCount(); c++) {
            size[groups.nodeOf(c)] += instance.studentsOf(c).length;
        }

        int[] nodes = new int[16];
        boolean[] hard = new boolean[16];
        for (int s = 0; s < instance.studentCount(); s++) {
            int[] courses = instance.coursesOf(s);
            if (nodes.length < courses.length) {
                nodes = new int[courses.length];
                hard = new boolean[courses.length];
            }
            int k = 0;
            for (int c : courses) {
                int v = groups.nodeOf(c);
                int existing = -1;
                for (int i = 0; i < k; i++) {
                    if (nodes[i] == v) {
                        existing = i;
                        break;
                    }
                }
                if (existing >= 0) {
                    int other = firstCourseOfNode(groups, courses, v, c);
                    internal.add(new InternalConflict(v, s, other, c));
                    hard[existing] |= instance.course(c).hard();
                } else {
                    nodes[k] = v;
                    hard[k] = instance.course(c).hard();
                    k++;
                }
            }
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    int a = Math.min(nodes[i], nodes[j]);
                    int b = Math.max(nodes[i], nodes[j]);
                    int[] w = edges.computeIfAbsent((long) a * n + b, x -> new int[2]);
                    w[0]++;
                    if (hard[i] && hard[j]) {
                        w[1]++;
                    }
                }
            }
        }

        // Collect both directions of every edge, then sort each adjacency list by neighbour.
        List<List<int[]>> adjacency = new ArrayList<>(n);
        for (int v = 0; v < n; v++) {
            adjacency.add(new ArrayList<>());
        }
        edges.forEach((e, w) -> {
            int a = (int) (e / n);
            int b = (int) (e % n);
            adjacency.get(a).add(new int[] {b, w[0], w[1]});
            adjacency.get(b).add(new int[] {a, w[0], w[1]});
        });
        int[][] nb = new int[n][];
        int[][] wt = new int[n][];
        int[][] hw = new int[n][];
        for (int v = 0; v < n; v++) {
            List<int[]> list = adjacency.get(v);
            list.sort((x, y) -> Integer.compare(x[0], y[0]));
            nb[v] = new int[list.size()];
            wt[v] = new int[list.size()];
            hw[v] = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                nb[v][i] = list.get(i)[0];
                wt[v][i] = list.get(i)[1];
                hw[v][i] = list.get(i)[2];
            }
        }
        return new ConflictGraph(groups, nb, wt, hw, size, edges.size(), internal);
    }

    private static int firstCourseOfNode(ExamGroups groups, int[] courses, int node,
                                         int except) {
        for (int c : courses) {
            if (c != except && groups.nodeOf(c) == node) {
                return c;
            }
        }
        throw new IllegalStateException("no other course of node " + node + " among " + Arrays.toString(courses));
    }

    public ExamGroups groups() {
        return groups;
    }

    public int nodeCount() {
        return neighbours.length;
    }

    public int edgeCount() {
        return edgeCount;
    }

    public int[] neighbours(int v) {
        return neighbours[v];
    }

    /** Shared-student counts, parallel to {@link #neighbours(int)}. */
    public int[] weights(int v) {
        return weights[v];
    }

    /** Shared hard-paper student counts, parallel to {@link #neighbours(int)}. */
    public int[] hardWeights(int v) {
        return hardWeights[v];
    }

    public int degree(int v) {
        return neighbours[v].length;
    }

    public int maxDegree() {
        int max = 0;
        for (int[] nb : neighbours) {
            max = Math.max(max, nb.length);
        }
        return max;
    }

    /** Number of students sharing nodes {@code a} and {@code b}; 0 when not adjacent. */
    public int weight(int a, int b) {
        int i = Arrays.binarySearch(neighbours[a], b);
        return i < 0 ? 0 : weights[a][i];
    }

    public boolean adjacent(int a, int b) {
        return Arrays.binarySearch(neighbours[a], b) >= 0;
    }

    /** Total number of exam seats needed by a node (sum of its courses' enrolments). */
    public int nodeSize(int v) {
        return nodeSize[v];
    }

    public List<InternalConflict> internalConflicts() {
        return internalConflicts;
    }

    /**
     * Up to {@code limit} students (indexes) who sit both nodes, found by merging the sorted
     * student lists of the nodes' courses.
     */
    public static int[] sharedStudents(ProblemInstance instance, ExamGroups groups, int a, int b, int limit) {
        int[] left = studentsOfNode(instance, groups, a);
        int[] right = studentsOfNode(instance, groups, b);
        int[] out = new int[Math.min(limit, Math.min(left.length, right.length))];
        int i = 0;
        int j = 0;
        int k = 0;
        while (i < left.length && j < right.length && k < out.length) {
            if (left[i] == right[j]) {
                out[k++] = left[i];
                i++;
                j++;
            } else if (left[i] < right[j]) {
                i++;
            } else {
                j++;
            }
        }
        return Arrays.copyOf(out, k);
    }

    /** Sorted, distinct student indexes of every course in a node. */
    public static int[] studentsOfNode(ProblemInstance instance, ExamGroups groups, int node) {
        int[] courses = groups.coursesOf(node);
        if (courses.length == 1) {
            return instance.studentsOf(courses[0]);
        }
        return Arrays.stream(courses)
                .flatMap(c -> Arrays.stream(instance.studentsOf(c)))
                .distinct()
                .sorted()
                .toArray();
    }
}
