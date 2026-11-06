package com.examgrid.backend.imports.handlers;

import com.examgrid.backend.catalog.CatalogService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.imports.ImportHandler;
import com.examgrid.backend.imports.ImportIssue;
import com.examgrid.backend.imports.ImportKind;
import com.examgrid.backend.imports.ImportMode;
import com.examgrid.backend.imports.ImportSupport;
import com.examgrid.backend.imports.TabularFileReader.DataRow;
import com.examgrid.backend.imports.TabularFileReader.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code code,rows,columns[,building,blocked,priority]}; upserts by code. Priority defaults to file order. */
@Component
public class RoomImportHandler implements ImportHandler {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RoomImportHandler(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    record RoomRow(String code, String building, int rows, int columns, List<String> blocked, int priority) {
        int capacity() {
            return rows * columns - blocked.size();
        }
    }

    @Override
    public ImportKind kind() {
        return ImportKind.ROOMS;
    }

    @Override
    public Validation validate(Table table, ImportMode mode) {
        List<ImportIssue> issues = new ArrayList<>();
        if (!ImportSupport.checkHeaders(table, kind(), issues)) {
            return new Validation(issues, 0, Map.of(), null);
        }
        Set<String> existing = new HashSet<>(jdbc.queryForList("select code from room", String.class));
        Map<String, RoomRow> rows = new LinkedHashMap<>();
        Map<String, Integer> firstRow = new HashMap<>();
        int order = 0;
        for (DataRow row : table.rows()) {
            order++;
            int errors = issues.size();
            String code = ImportSupport.code(row, "code", issues);
            String building = ImportSupport.optional(row, "building", 120, issues);
            Integer r = ImportSupport.integer(row, "rows", 1, 100, null, issues);
            Integer c = ImportSupport.integer(row, "columns", 1, 100, null, issues);
            Integer priority = ImportSupport.integer(row, "priority", 0, 10_000, order, issues);
            List<String> blocked = List.of();
            if (r != null && c != null) {
                try {
                    blocked = CatalogService.validateLayout(r, c, ImportSupport.list(row, "blocked"));
                } catch (ApiException e) {
                    issues.add(ImportIssue.error(row.rowNumber(), "blocked", e.getMessage()));
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
            rows.put(code, new RoomRow(code, building, r, c, blocked, priority));
        }
        int created = (int) rows.keySet().stream().filter(code -> !existing.contains(code)).count();
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("updated", rows.size() - created);
        changes.put("seats", rows.values().stream().mapToInt(RoomRow::capacity).sum());
        return new Validation(issues, rows.size(), changes, List.copyOf(rows.values()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void commit(Validation validation, ImportMode mode, long batchId) {
        List<RoomRow> rows = (List<RoomRow>) validation.plan();
        jdbc.batchUpdate("""
                insert into room (code, building, seat_rows, seat_columns, blocked_seats, capacity, priority)
                values (?, ?, ?, ?, ?::jsonb, ?, ?)
                on conflict (code) do update set building = excluded.building, seat_rows = excluded.seat_rows,
                    seat_columns = excluded.seat_columns, blocked_seats = excluded.blocked_seats,
                    capacity = excluded.capacity, priority = excluded.priority
                """, rows.stream().map(r -> new Object[] {r.code(), r.building(), r.rows(), r.columns(),
                mapper.writeValueAsString(r.blocked()), r.capacity(), r.priority()}).toList());
    }
}
