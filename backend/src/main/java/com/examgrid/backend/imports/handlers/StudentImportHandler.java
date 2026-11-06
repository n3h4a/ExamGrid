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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** {@code roll[,name,department,email]}; upserts by roll number. */
@Component
public class StudentImportHandler implements ImportHandler {

    private final JdbcTemplate jdbc;
    private final Pattern rollPattern;

    public StudentImportHandler(JdbcTemplate jdbc, ImportProperties properties) {
        this.jdbc = jdbc;
        this.rollPattern = Pattern.compile(properties.rollPattern());
    }

    record StudentRow(String roll, String name, String department, String email) {
    }

    @Override
    public ImportKind kind() {
        return ImportKind.STUDENTS;
    }

    @Override
    public Validation validate(Table table, ImportMode mode) {
        List<ImportIssue> issues = new ArrayList<>();
        if (!ImportSupport.checkHeaders(table, kind(), issues)) {
            return new Validation(issues, 0, Map.of(), null);
        }
        Map<String, StudentRow> existing = new HashMap<>();
        jdbc.query("select roll, name, department, email from student", rs -> {
            existing.put(rs.getString(1), new StudentRow(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4)));
        });
        Map<String, StudentRow> rows = new LinkedHashMap<>();
        Map<String, Integer> firstRow = new HashMap<>();
        for (DataRow row : table.rows()) {
            int errors = issues.size();
            String roll = ImportSupport.code(row, "roll", issues);
            if (roll != null && !rollPattern.matcher(roll).matches()) {
                issues.add(ImportIssue.error(row.rowNumber(), "roll", "'" + row.get("roll")
                        + "' does not look like a roll number (expected pattern " + rollPattern.pattern() + ")."));
            }
            String name = ImportSupport.optional(row, "name", 120, issues);
            String department = ImportSupport.optional(row, "department", 64, issues);
            String email = ImportSupport.email(row, issues);
            if (issues.size() > errors) {
                continue;
            }
            Integer first = firstRow.putIfAbsent(roll, row.rowNumber());
            if (first != null) {
                issues.add(ImportIssue.warning(row.rowNumber(), "roll", "Duplicate of row " + first + "; skipped."));
                continue;
            }
            rows.put(roll, new StudentRow(roll, name, department, email));
        }
        int created = 0;
        int updated = 0;
        for (StudentRow r : rows.values()) {
            StudentRow old = existing.get(r.roll());
            if (old == null) {
                created++;
            } else if (!Objects.equals(old.name(), r.name()) || !Objects.equals(old.department(), r.department())
                    || !Objects.equals(old.email(), r.email())) {
                updated++;
            }
        }
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("updated", updated);
        changes.put("unchanged", rows.size() - created - updated);
        return new Validation(issues, rows.size(), changes, List.copyOf(rows.values()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit(Validation validation, ImportMode mode, long batchId) {
        List<StudentRow> rows = (List<StudentRow>) validation.plan();
        jdbc.batchUpdate("""
                insert into student (roll, name, department, email) values (?, ?, ?, ?)
                on conflict (roll) do update set name = excluded.name, department = excluded.department,
                    email = excluded.email
                """, rows.stream().map(r -> new Object[] {r.roll(), r.name(), r.department(), r.email()}).toList());
    }
}
