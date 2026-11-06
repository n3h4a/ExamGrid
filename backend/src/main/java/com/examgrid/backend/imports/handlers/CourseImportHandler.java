package com.examgrid.backend.imports.handlers;

import com.examgrid.backend.imports.ImportHandler;
import com.examgrid.backend.imports.ImportIssue;
import com.examgrid.backend.imports.ImportKind;
import com.examgrid.backend.imports.ImportMode;
import com.examgrid.backend.imports.ImportSupport;
import com.examgrid.backend.imports.TabularFileReader.DataRow;
import com.examgrid.backend.imports.TabularFileReader.Table;
import com.examgrid.solver.model.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code code,name[,department,hard,sessions]}; upserts by code. */
@Component
public class CourseImportHandler implements ImportHandler {

    private final JdbcTemplate jdbc;

    public CourseImportHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record CourseRow(String code, String name, String department, boolean hard, String sessions) {
    }

    @Override
    public ImportKind kind() {
        return ImportKind.COURSES;
    }

    @Override
    public Validation validate(Table table, ImportMode mode) {
        List<ImportIssue> issues = new ArrayList<>();
        if (!ImportSupport.checkHeaders(table, kind(), issues)) {
            return new Validation(issues, 0, Map.of(), null);
        }
        Map<String, CourseRow> existing = new HashMap<>();
        jdbc.query("select code, name, department, hard, allowed_sessions from course", rs -> {
            existing.put(rs.getString(1), new CourseRow(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getBoolean(4), rs.getString(5)));
        });
        Map<String, CourseRow> rows = new LinkedHashMap<>();
        Map<String, Integer> firstRow = new HashMap<>();
        for (DataRow row : table.rows()) {
            int errors = issues.size();
            String code = ImportSupport.code(row, "code", issues);
            String name = ImportSupport.required(row, "name", 200, issues);
            String department = ImportSupport.optional(row, "department", 64, issues);
            Boolean hard = ImportSupport.bool(row, "hard", issues);
            String sessions = sessions(row, issues);
            if (issues.size() > errors) {
                continue;
            }
            CourseRow parsed = new CourseRow(code, name, department, hard, sessions);
            Integer first = firstRow.putIfAbsent(code, row.rowNumber());
            if (first != null) {
                if (rows.get(code).equals(parsed)) {
                    issues.add(ImportIssue.warning(row.rowNumber(), "code", "Duplicate of row " + first + "; skipped."));
                } else {
                    issues.add(ImportIssue.error(row.rowNumber(), "code", code + " already appears on row " + first
                            + " with different details."));
                }
                continue;
            }
            rows.put(code, parsed);
        }
        int created = 0;
        int updated = 0;
        for (CourseRow r : rows.values()) {
            CourseRow old = existing.get(r.code());
            if (old == null) {
                created++;
            } else if (!old.equals(r)) {
                updated++;
            }
        }
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("updated", updated);
        changes.put("unchanged", rows.size() - created - updated);
        return new Validation(issues, rows.size(), changes, List.copyOf(rows.values()));
    }

    private static String sessions(DataRow row, List<ImportIssue> issues) {
        Set<Session> sessions = EnumSet.noneOf(Session.class);
        for (String raw : ImportSupport.list(row, "sessions")) {
            try {
                sessions.add(Session.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                issues.add(ImportIssue.error(row.rowNumber(), "sessions", "'" + raw
                        + "' is not a session (MORNING, AFTERNOON or EVENING)."));
            }
        }
        return sessions.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit(Validation validation, ImportMode mode, long batchId) {
        List<CourseRow> rows = (List<CourseRow>) validation.plan();
        jdbc.batchUpdate("""
                insert into course (code, name, department, hard, allowed_sessions) values (?, ?, ?, ?, ?)
                on conflict (code) do update set name = excluded.name, department = excluded.department,
                    hard = excluded.hard, allowed_sessions = excluded.allowed_sessions, updated_at = now()
                """, rows.stream().map(r -> new Object[] {r.code(), r.name(), r.department(), r.hard(), r.sessions()})
                .toList());
    }
}
