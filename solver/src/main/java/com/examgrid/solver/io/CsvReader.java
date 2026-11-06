package com.examgrid.solver.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RFC 4180 reader (quoted fields, escaped quotes, CRLF) so the solver module stays
 * dependency-free. The first record is the header; column names are matched
 * case-insensitively.
 */
public final class CsvReader {

    private CsvReader() {
    }

    /** A data row addressed by header name. */
    public record Row(int lineNumber, Map<String, String> values) {
        public String get(String column) {
            String v = values.get(column.toLowerCase(Locale.ROOT));
            return v == null ? "" : v.strip();
        }

        public boolean has(String column) {
            return !get(column).isEmpty();
        }
    }

    public static List<Row> read(Reader source) throws IOException {
        List<List<String>> records = parse(source);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst().stream()
                .map(h -> h.strip().replace("﻿", "").toLowerCase(Locale.ROOT))
                .toList();
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> rec = records.get(i);
            if (rec.size() == 1 && rec.getFirst().isBlank()) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                values.put(header.get(c), c < rec.size() ? rec.get(c) : "");
            }
            rows.add(new Row(i + 1, values));
        }
        return rows;
    }

    static List<List<String>> parse(Reader source) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        try (BufferedReader in = new BufferedReader(source)) {
            int ch;
            while ((ch = in.read()) != -1) {
                char c = (char) ch;
                any = true;
                if (quoted) {
                    if (c == '"') {
                        in.mark(1);
                        int next = in.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                in.reset();
                            }
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"' && field.isEmpty()) {
                    quoted = true;
                } else if (c == ',') {
                    current.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n' || c == '\r') {
                    if (c == '\r') {
                        in.mark(1);
                        if (in.read() != '\n') {
                            in.reset();
                        }
                    }
                    current.add(field.toString());
                    field.setLength(0);
                    records.add(current);
                    current = new ArrayList<>();
                    any = false;
                } else {
                    field.append(c);
                }
            }
        }
        if (any || !field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }
}
