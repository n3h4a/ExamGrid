package com.examgrid.solver.graph;

import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.util.UnionFind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses courses that must sit in the same slot ("common paper across branches") into a
 * single exam node using union-find. Every other course is a node of its own. The conflict
 * graph and the timetable work on nodes; rooms and seating work on courses.
 */
public final class ExamGroups {

    private final int[] nodeOfCourse;
    private final int[][] coursesOfNode;

    private ExamGroups(int[] nodeOfCourse, int[][] coursesOfNode) {
        this.nodeOfCourse = nodeOfCourse;
        this.coursesOfNode = coursesOfNode;
    }

    public static ExamGroups of(ProblemInstance instance) {
        int n = instance.courseCount();
        UnionFind uf = new UnionFind(n);
        for (int[] group : instance.sameSlotGroups()) {
            for (int i = 1; i < group.length; i++) {
                uf.union(group[0], group[i]);
            }
        }
        int[] nodeOf = new int[n];
        Map<Integer, Integer> nodeOfRoot = new HashMap<>();
        List<List<Integer>> members = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            int root = uf.find(c);
            Integer node = nodeOfRoot.get(root);
            if (node == null) {
                node = members.size();
                nodeOfRoot.put(root, node);
                members.add(new ArrayList<>());
            }
            nodeOf[c] = node;
            members.get(node).add(c);
        }
        int[][] coursesOf = new int[members.size()][];
        for (int v = 0; v < members.size(); v++) {
            coursesOf[v] = members.get(v).stream().mapToInt(Integer::intValue).toArray();
        }
        return new ExamGroups(nodeOf, coursesOf);
    }

    public int nodeCount() {
        return coursesOfNode.length;
    }

    public int nodeOf(int course) {
        return nodeOfCourse[course];
    }

    public int[] coursesOf(int node) {
        return coursesOfNode[node];
    }
}
