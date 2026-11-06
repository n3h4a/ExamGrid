package com.examgrid.backend.imports.handlers;

import com.examgrid.backend.imports.ImportHandler;
import com.examgrid.backend.imports.ImportIssue;
import com.examgrid.backend.imports.ImportKind;
import com.examgrid.backend.imports.ImportMode;
import com.examgrid.backend.imports.ImportProperties;
import com.examgrid.backend.imports.ImportSupport;
import com.examgrid.backend.imports.TabularFileReader.DataRow;
import com.examgrid.backend.imports.TabularFileReader.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code roll,course[,name,department]}. Students are created on first sight (and their name or
 * department updated when given); courses must already exist, because a typo in a course code
 * would otherwise silently create a phantom exam. Duplicate rows are skipped with a warning.
 * In {@link ImportMode#REPLACE} mode the file becomes the complete enrolment list.
 */
@Component
public class EnrolmentImportHandler implements ImportHandler {

    private final JdbcTemplate jdbc;
    private final Pattern rollPattern;

    public EnrolmentImportHandler(JdbcTemplate jdbc, ImportProperties properties) {
        this.jdbc = jdbc;
        this.rollPattern = Pattern.compile(properties.rollPattern());
    }

    record StudentRow(String roll, String name, String department) {
    }

    record Plan(Map<String, StudentRow> students, List<String[]> pairs) {
    }

    @Override
    public ImportKind kind() {
        return ImportKind.ENROLMENTS;
    }

    @Override
    public Validation validate(Table table, ImportMode mode) {
        List<ImportIssue> issues = new ArrayList<>();
        if (!ImportSupport.checkHeaders(table, kind(), issues)) {
            return new Validation(issues, 0, Map.of(), null);
        }
        Map<String, Long> courseIds = new HashMap<>();
        jdbc.query("select id, code from course", rs -> {
            courseIds.put(rs.getString("code"), rs.getLong("id"));
        });
        Map<String, String[]> existingStudents = new HashMap<>();
        jdbc.query("select roll, name, department from student", rs -> {
            existingStudents.put(rs.getString("roll"), new String[] {rs.getString("name"), rs.getString("department")});
        });
        Set<String> existingPairs = new HashSet<>();
        jdbc.query("select s.roll, c.code from enrolment e join student s on s.id = e.student_id "
                + "join course c on c.id = e.course_id", rs -> {
            existingPairs.add(rs.getString(1) + "|" + rs.getString(2));
        });

        Map<String, StudentRow> students = new LinkedHashMap<>();
        Map<String, Integer> firstRowOfPair = new HashMap<>();
        List<String[]> pairs = new ArrayList<>();
        for (DataRow row : table.rows()) {
            int before = errorCount(issues);
            String roll = ImportSupport.code(row, "roll", issues);
            if (roll != null && !rollPattern.matcher(roll).matches()) {
                issues.add(ImportIssue.error(row.rowNumber(), "roll", "'" + row.get("roll")
                        + "' does not look like a roll number (expected pattern " + rollPattern.pattern() + ")."));
                roll = null;
            }
            String course = ImportSupport.code(row, "course", issues);
            if (course != null && !courseIds.containsKey(course)) {
                issues.add(ImportIssue.error(row.rowNumber(), "course", "Unknown course " + course
                        + ". Import or create the course first."));
                course = null;
            }
            String name = ImportSupport.optional(row, "name", 120, issues);
            String department = ImportSupport.optional(row, "department", 64, issues);
            if (errorCount(issues) > before || roll == null || course == null) {
                continue;
            }
            String key = roll + "|" + course;
            Integer first = firstRowOfPair.putIfAbsent(key, row.rowNumber());
            if (first != null) {
                issues.add(ImportIssue.warning(row.rowNumber(), null, "Duplicate of row " + first + " (" + roll
                        + " in " + course + "); skipped."));
                continue;
            }
            StudentRow previous = students.get(roll);
            if (previous == null || (previous.name().isEmpty() && name != null && !name.isEmpty())) {
                students.put(roll, new StudentRow(roll, name == null ? "" : name, department == null ? "" : department));
            }
            pairs.add(new String[] {roll, course});
        }

        int added = 0;
        Set<String> filePairs = new HashSet<>();
        for (String[] p : pairs) {
            String key = p[0] + "|" + p[1];
            filePairs.add(key);
            if (!existingPairs.contains(key)) {
                added++;
            }
        }
        int removed = 0;
        if (mode == ImportMode.REPLACE) {
            for (String key : existingPairs) {
                if (!filePairs.contains(key)) {
                    removed++;
                }
            }
        }
        int newStudents = 0;
        int updatedStudents = 0;
        for (StudentRow s : students.values()) {
            String[] existing = existingStudents.get(s.roll());
            if (existing == null) {
                newStudents++;
            } else if ((!s.name().isEmpty() && !s.name().equals(existing[0]))
                    || (!s.department().isEmpty() && !s.department().equals(existing[1]))) {
                updatedStudents++;
            }
        }
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("enrolmentsAdded", added);
        changes.put("alreadyEnrolled", pairs.size() - added);
        changes.put("enrolmentsRemoved", removed);
        changes.put("studentsCreated", newStudents);
        changes.put("studentsUpdated", updatedStudents);
        changes.put("courses", (int) pairs.stream().map(p -> p[1]).distinct().count());
        return new Validation(issues, pairs.size(), changes, new Plan(students, pairs));
    }

    @Override
    public void commit(Validation validation, ImportMode mode, long batchId) {
        Plan plan = (Plan) validation.plan();
        List<Object[]> studentArgs = plan.students().values().stream()
                .map(s -> new Object[] {s.roll(), s.name(), s.department()}).toList();
        jdbc.batchUpdate("""
                insert into student (roll, name, department) values (?, ?, ?)
                on conflict (roll) do update set
                    name = case when excluded.name <> '' then excluded.name else student.name end,
                    department = case when excluded.department <> '' then excluded.department else student.department end
                """, studentArgs);
        Map<String, Long> studentIds = new HashMap<>();
        jdbc.query("select id, roll from student", rs -> {
            studentIds.put(rs.getString("roll"), rs.getLong("id"));
        });
        Map<String, Long> courseIds = new HashMap<>();
        jdbc.query("select id, code from course", rs -> {
            courseIds.put(rs.getString("code"), rs.getLong("id"));
        });
        List<Object[]> pairArgs = plan.pairs().stream()
                .map(p -> new Object[] {studentIds.get(p[0]), courseIds.get(p[1]), batchId}).toList();
        jdbc.batchUpdate("""
                insert into enrolment (student_id, course_id, import_batch_id) values (?, ?, ?)
                on conflict (student_id, course_id) do update set import_batch_id = excluded.import_batch_id
                """, pairArgs);
        if (mode == ImportMode.REPLACE) {
            jdbc.update("delete from enrolment where import_batch_id is distinct from ?", batchId);
        }
    }

    private static int errorCount(List<ImportIssue> issues) {
        return (int) issues.stream().filter(i -> i.severity() == ImportIssue.Severity.ERROR).count();
    }
}
