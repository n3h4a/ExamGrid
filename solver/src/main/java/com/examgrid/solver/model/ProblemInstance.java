package com.examgrid.solver.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, validated input of a solve. Besides the raw records it keeps dense integer
 * indexes (course, student, slot, room) that the algorithms work on.
 *
 * <p>Index conventions: courses keep insertion order, students are sorted by roll number,
 * slots are sorted chronologically and rooms keep insertion order, which doubles as their
 * fill priority.
 */
public final class ProblemInstance {

    private final List<Course> courses;
    private final List<String> students;
    private final List<Slot> slots;
    private final List<Room> rooms;
    private final List<Faculty> faculty;
    private final Map<String, Integer> courseIndex;
    private final Map<String, Integer> studentIndex;
    private final Map<String, Integer> slotIndex;
    private final Map<String, Integer> roomIndex;
    private final int[][] courseStudents;
    private final int[][] studentCourses;
    private final Map<Integer, Integer> lockedSlots;
    private final List<int[]> sameSlotGroups;
    private final SolverParams params;

    private ProblemInstance(Builder b) {
        this.params = Objects.requireNonNullElseGet(b.params, SolverParams::defaults);

        this.courses = List.copyOf(b.courses.values());
        this.courseIndex = indexBy(courses.stream().map(Course::code).toList());

        List<Slot> sortedSlots = new ArrayList<>(b.slots.values());
        Collections.sort(sortedSlots);
        this.slots = List.copyOf(sortedSlots);
        this.slotIndex = indexBy(slots.stream().map(Slot::id).toList());

        this.rooms = List.copyOf(b.rooms.values());
        this.roomIndex = indexBy(rooms.stream().map(Room::code).toList());
        this.faculty = List.copyOf(b.faculty.values());

        TreeSet<String> rolls = new TreeSet<>();
        for (Enrolment e : b.enrolments) {
            if (!courseIndex.containsKey(e.courseCode())) {
                throw new InvalidInstanceException(
                        "enrolment of " + e.roll() + " references unknown course " + e.courseCode());
            }
            rolls.add(e.roll());
        }
        this.students = List.copyOf(rolls);
        this.studentIndex = indexBy(students);

        List<Set<Integer>> perCourse = new ArrayList<>();
        List<Set<Integer>> perStudent = new ArrayList<>();
        courses.forEach(c -> perCourse.add(new TreeSet<>()));
        students.forEach(s -> perStudent.add(new TreeSet<>()));
        for (Enrolment e : b.enrolments) {
            int c = courseIndex.get(e.courseCode());
            int s = studentIndex.get(e.roll());
            perCourse.get(c).add(s);
            perStudent.get(s).add(c);
        }
        this.courseStudents = toArrays(perCourse);
        this.studentCourses = toArrays(perStudent);

        Map<Integer, Integer> locks = new LinkedHashMap<>();
        b.locks.forEach((courseCode, slotId) -> {
            Integer c = courseIndex.get(courseCode);
            if (c == null) {
                throw new InvalidInstanceException("lock references unknown course " + courseCode);
            }
            Integer s = slotIndex.get(slotId);
            if (s == null) {
                throw new InvalidInstanceException("lock of " + courseCode + " references unknown slot " + slotId);
            }
            locks.put(c, s);
        });
        this.lockedSlots = Collections.unmodifiableMap(locks);

        List<int[]> groups = new ArrayList<>();
        for (Set<String> group : b.sameSlotGroups) {
            int[] members = group.stream().mapToInt(code -> {
                Integer c = courseIndex.get(code);
                if (c == null) {
                    throw new InvalidInstanceException("same-slot group references unknown course " + code);
                }
                return c;
            }).distinct().sorted().toArray();
            if (members.length > 1) {
                groups.add(members);
            }
        }
        this.sameSlotGroups = List.copyOf(groups);

        Set<String> facultyCourseRefs = new HashSet<>();
        Set<String> facultySlotRefs = new HashSet<>();
        faculty.forEach(f -> {
            facultyCourseRefs.addAll(f.teaches());
            facultySlotRefs.addAll(f.unavailableSlotIds());
        });
        for (String code : facultyCourseRefs) {
            if (!courseIndex.containsKey(code)) {
                throw new InvalidInstanceException("faculty teaches unknown course " + code);
            }
        }
        for (String id : facultySlotRefs) {
            if (!slotIndex.containsKey(id)) {
                throw new InvalidInstanceException("faculty unavailability references unknown slot " + id);
            }
        }
    }

    private static Map<String, Integer> indexBy(List<String> keys) {
        Map<String, Integer> index = new HashMap<>(keys.size() * 2);
        for (int i = 0; i < keys.size(); i++) {
            index.put(keys.get(i), i);
        }
        return Collections.unmodifiableMap(index);
    }

    private static int[][] toArrays(List<Set<Integer>> sets) {
        int[][] out = new int[sets.size()][];
        for (int i = 0; i < sets.size(); i++) {
            out[i] = sets.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Copy of this instance with different solver parameters. */
    public ProblemInstance withParams(SolverParams newParams) {
        return toBuilder().params(newParams).build();
    }

    /** Builder pre-populated with this instance's data, e.g. to add locks or drop a room. */
    public Builder toBuilder() {
        Builder b = new Builder().params(params);
        courses.forEach(b::course);
        slots.forEach(b::slot);
        rooms.forEach(b::room);
        faculty.forEach(b::faculty);
        for (int c = 0; c < courses.size(); c++) {
            for (int s : courseStudents[c]) {
                b.enrol(students.get(s), courses.get(c).code());
            }
        }
        lockedSlots.forEach((c, s) -> b.lock(courses.get(c).code(), slots.get(s).id()));
        for (int[] group : sameSlotGroups) {
            Set<String> codes = new LinkedHashSet<>();
            for (int c : group) {
                codes.add(courses.get(c).code());
            }
            b.sameSlot(codes);
        }
        return b;
    }

    public List<Course> courses() { return courses; }
    public List<String> students() { return students; }
    public List<Slot> slots() { return slots; }
    public List<Room> rooms() { return rooms; }
    public List<Faculty> faculty() { return faculty; }
    public SolverParams params() { return params; }

    public int courseCount() { return courses.size(); }
    public int studentCount() { return students.size(); }
    public int slotCount() { return slots.size(); }
    public int roomCount() { return rooms.size(); }

    public Course course(int index) { return courses.get(index); }
    public String student(int index) { return students.get(index); }
    public Slot slot(int index) { return slots.get(index); }
    public Room room(int index) { return rooms.get(index); }

    public int courseIndex(String code) { return lookup(courseIndex, code, "course"); }
    public int studentIndex(String roll) { return lookup(studentIndex, roll, "student"); }
    public int slotIndex(String id) { return lookup(slotIndex, id, "slot"); }
    public int roomIndex(String code) { return lookup(roomIndex, code, "room"); }

    public boolean hasRoom(String code) { return roomIndex.containsKey(code); }

    private static int lookup(Map<String, Integer> index, String key, String what) {
        Integer i = index.get(key);
        if (i == null) {
            throw new InvalidInstanceException("unknown " + what + " " + key);
        }
        return i;
    }

    /** Student indexes enrolled in a course, ascending (i.e. sorted by roll number). */
    public int[] studentsOf(int course) { return courseStudents[course]; }

    /** Course indexes a student is enrolled in, ascending. */
    public int[] coursesOf(int student) { return studentCourses[student]; }

    public int enrolmentCount() {
        return Arrays.stream(courseStudents).mapToInt(a -> a.length).sum();
    }

    /** Locked (pinned) slot per course index. */
    public Map<Integer, Integer> lockedSlots() { return lockedSlots; }

    /** Groups of course indexes that must share a slot (e.g. common papers across branches). */
    public List<int[]> sameSlotGroups() { return sameSlotGroups; }

    /** Total seats across all rooms. */
    public int totalSeats() {
        return rooms.stream().mapToInt(Room::capacity).sum();
    }

    /** Mutable builder; repeated keys replace earlier values, duplicate enrolments are ignored. */
    public static final class Builder {
        private final Map<String, Course> courses = new LinkedHashMap<>();
        private final Map<String, Slot> slots = new LinkedHashMap<>();
        private final Map<String, Room> rooms = new LinkedHashMap<>();
        private final Map<String, Faculty> faculty = new LinkedHashMap<>();
        private final Set<Enrolment> enrolments = new LinkedHashSet<>();
        private final Map<String, String> locks = new LinkedHashMap<>();
        private final List<Set<String>> sameSlotGroups = new ArrayList<>();
        private SolverParams params;

        private Builder() {
        }

        public Builder course(Course c) { courses.put(c.code(), c); return this; }
        public Builder courses(Collection<Course> cs) { cs.forEach(this::course); return this; }
        public Builder slot(Slot s) { slots.put(s.id(), s); return this; }
        public Builder slots(Collection<Slot> ss) { ss.forEach(this::slot); return this; }
        public Builder room(Room r) { rooms.put(r.code(), r); return this; }
        public Builder rooms(Collection<Room> rs) { rs.forEach(this::room); return this; }
        public Builder faculty(Faculty f) { faculty.put(f.id(), f); return this; }
        public Builder faculty(Collection<Faculty> fs) { fs.forEach(this::faculty); return this; }
        public Builder enrol(String roll, String courseCode) { enrolments.add(new Enrolment(roll, courseCode)); return this; }
        public Builder enrolments(Collection<Enrolment> es) { enrolments.addAll(es); return this; }
        public Builder lock(String courseCode, String slotId) { locks.put(courseCode, slotId); return this; }
        public Builder unlock(String courseCode) { locks.remove(courseCode); return this; }
        public Builder sameSlot(Set<String> courseCodes) { sameSlotGroups.add(Set.copyOf(courseCodes)); return this; }
        public Builder params(SolverParams p) { this.params = p; return this; }
        public Builder removeRoom(String code) { rooms.remove(code); return this; }

        public ProblemInstance build() {
            if (slots.isEmpty()) {
                throw new InvalidInstanceException("at least one exam slot is required");
            }
            return new ProblemInstance(this);
        }
    }
}
