package com.examgrid.backend.imports.handlers;

import com.examgrid.backend.catalog.SlotKeys;
import com.examgrid.backend.imports.ImportHandler;
import com.examgrid.backend.imports.ImportIssue;
import com.examgrid.backend.imports.ImportKind;
import com.examgrid.backend.imports.ImportMode;
import com.examgrid.backend.imports.ImportSupport;
import com.examgrid.backend.imports.TabularFileReader.DataRow;
import com.examgrid.backend.imports.TabularFileReader.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code code,name[,email,department,teaches,unavailable]}. {@code teaches} lists course codes;
 * {@code unavailable} lists dates (the whole day) or slots such as {@code 2026-12-14-AFTERNOON}.
 * A re-import replaces each listed person's teaching and unavailability.
 */
@Component
public class FacultyImportHandler implements ImportHandler {

    private final JdbcTemplate jdbc;

    public FacultyImportHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record FacultyRow(String code, String name, String email, String department, Set<Long> courseIds,
                      Set<Long> slotIds) {
    }

    @Override
    public ImportKind kind() {
        return ImportKind.FACULTY;
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
        Map<LocalDate, Map<String, Long>> slotsByDate = new HashMap<>();
        jdbc.query("select id, slot_date, session from exam_slot", rs -> {
            slotsByDate.computeIfAbsent(rs.getDate("slot_date").toLocalDate(), d -> new HashMap<>())
                    .put(rs.getString("session"), rs.getLong("id"));
        });
        Set<String> existing = new HashSet<>(jdbc.queryForList("select code from faculty", String.class));

        Map<String, FacultyRow> rows = new LinkedHashMap<>();
        Map<String, Integer> firstRow = new HashMap<>();
        for (DataRow row : table.rows()) {
            int errors = issues.size();
            String code = ImportSupport.code(row, "code", issues);
            String name = ImportSupport.required(row, "name", 120, issues);
            String email = ImportSupport.email(row, issues);
            String department = ImportSupport.optional(row, "department", 64, issues);
            Set<Long> teaches = new LinkedHashSet<>();
            for (String raw : ImportSupport.list(row, "teaches")) {
                Long id = courseIds.get(raw.toUpperCase(Locale.ROOT));
                if (id == null) {
                    issues.add(ImportIssue.error(row.rowNumber(), "teaches", "Unknown course " + raw + "."));
                } else {
                    teaches.add(id);
                }
            }
            Set<Long> away = new LinkedHashSet<>();
            for (String raw : ImportSupport.list(row, "unavailable")) {
                SlotKeys.Key key = SlotKeys.parse(raw).orElse(null);
                Map<String, Long> day = key == null ? null : slotsByDate.get(key.date());
                if (key == null) {
                    issues.add(ImportIssue.error(row.rowNumber(), "unavailable", "'" + raw
                            + "' is not a date (2026-12-14) or slot (2026-12-14-AFTERNOON)."));
                } else if (day == null || (key.session() != null && !day.containsKey(key.session().name()))) {
                    issues.add(ImportIssue.error(row.rowNumber(), "unavailable", "No exam slot on " + raw
                            + ". Create the exam slots before importing unavailability."));
                } else if (key.session() == null) {
                    away.addAll(day.values());
                } else {
                    away.add(day.get(key.session().name()));
                }
            }
            if (issues.size() > errors) {
                continue;
            }
            Integer first = firstRow.putIfAbsent(code, row.rowNumber());
            if (first != null) {
                issues.add(ImportIssue.error(row.rowNumber(), "code", code + " already appears on row " + first + "."));
                continue;
            }
            rows.put(code, new FacultyRow(code, name, email, department, teaches, away));
        }
        int created = (int) rows.keySet().stream().filter(c -> !existing.contains(c)).count();
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("updated", rows.size() - created);
        changes.put("teachingLinks", rows.values().stream().mapToInt(r -> r.courseIds().size()).sum());
        changes.put("unavailableSlots", rows.values().stream().mapToInt(r -> r.slotIds().size()).sum());
        return new Validation(issues, rows.size(), changes, List.copyOf(rows.values()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit(Validation validation, ImportMode mode, long batchId) {
        List<FacultyRow> rows = (List<FacultyRow>) validation.plan();
        jdbc.batchUpdate("""
                insert into faculty (code, name, email, department) values (?, ?, ?, ?)
                on conflict (code) do update set name = excluded.name, email = excluded.email,
                    department = excluded.department
                """, rows.stream().map(r -> new Object[] {r.code(), r.name(), r.email(), r.department()}).toList());
        Map<String, Long> ids = new HashMap<>();
        jdbc.query("select id, code from faculty", rs -> {
            ids.put(rs.getString("code"), rs.getLong("id"));
        });
        List<Object[]> clear = rows.stream().map(r -> new Object[] {ids.get(r.code())}).toList();
        jdbc.batchUpdate("delete from faculty_course where faculty_id = ?", clear);
        jdbc.batchUpdate("delete from faculty_unavailability where faculty_id = ?", clear);
        List<Object[]> teaches = new ArrayList<>();
        List<Object[]> away = new ArrayList<>();
        for (FacultyRow r : rows) {
            long id = ids.get(r.code());
            r.courseIds().forEach(c -> teaches.add(new Object[] {id, c}));
            r.slotIds().forEach(s -> away.add(new Object[] {id, s}));
        }
        jdbc.batchUpdate("insert into faculty_course (faculty_id, course_id) values (?, ?)", teaches);
        jdbc.batchUpdate("insert into faculty_unavailability (faculty_id, slot_id) values (?, ?)", away);
    }
}
