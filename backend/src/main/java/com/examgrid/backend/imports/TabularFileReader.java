package com.examgrid.backend.imports;

import com.examgrid.backend.common.error.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the first sheet of an {@code .xlsx} workbook, or a CSV (comma or semicolon separated,
 * UTF-8 with or without BOM), into header-keyed rows. Format is detected from the content
 * (the ZIP signature of xlsx), not just the file name. Cells are read as Excel displays them,
 * so a roll number typed as a number does not turn into {@code 2.1E7}.
 */
public final class TabularFileReader {

    public static final int MAX_ROWS = 200_000;
    private static final int MAX_COLUMNS = 50;

    private TabularFileReader() {
    }

    /** A parsed table: canonical headers and data rows (row numbers as the user sees them). */
    public record Table(List<String> headers, List<DataRow> rows) {
    }

    public record DataRow(int rowNumber, Map<String, String> values) {
        public String get(String column) {
            String v = values.get(column);
            return v == null ? "" : v.strip();
        }
    }

    public static Table read(byte[] content, String filename) {
        if (content.length == 0) {
            throw ApiException.badRequest("empty_file", "The file is empty.");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xls") || isOle2(content)) {
            throw ApiException.badRequest("unsupported_format",
                    "Old .xls workbooks are not supported; save the sheet as .xlsx or CSV.");
        }
        return isZip(content) ? readXlsx(content) : readCsv(content);
    }

    private static Table readCsv(byte[] content) {
        String text = decodeUtf8(content);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);
        // Keep blank lines as (empty) records so record numbers match the line numbers the user sees.
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(false)
                .setTrim(true).get();
        try (Reader reader = new java.io.StringReader(text); CSVParser parser = CSVParser.parse(reader, format)) {
            List<String> headers = null;
            List<DataRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> cells = new ArrayList<>();
                record.forEach(cells::add);
                if (headers == null && cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (headers == null) {
                    headers = headers(cells);
                    continue;
                }
                addRow(rows, headers, cells, (int) record.getRecordNumber());
            }
            if (headers == null) {
                throw ApiException.badRequest("empty_file", "The file has no header row.");
            }
            return new Table(headers, rows);
        } catch (IOException | IllegalStateException | java.io.UncheckedIOException e) {
            throw ApiException.badRequest("malformed_csv", "The CSV could not be read: " + e.getMessage());
        }
    }

    private static Table readXlsx(byte[] content) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw ApiException.badRequest("empty_file", "The workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<String> headers = null;
            List<DataRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                short last = row.getLastCellNum();
                for (int c = 0; c < Math.max(0, last); c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell).strip());
                }
                if (cells.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (headers == null) {
                    headers = headers(cells);
                    continue;
                }
                addRow(rows, headers, cells, row.getRowNum() + 1);
            }
            if (headers == null) {
                throw ApiException.badRequest("empty_file", "The first sheet has no header row.");
            }
            return new Table(headers, rows);
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw ApiException.badRequest("malformed_xlsx", "The workbook could not be read.");
        }
    }

    private static List<String> headers(List<String> cells) {
        if (cells.size() > MAX_COLUMNS) {
            throw ApiException.badRequest("too_many_columns", "At most " + MAX_COLUMNS + " columns are supported.");
        }
        List<String> headers = new ArrayList<>();
        for (String cell : cells) {
            headers.add(ImportKind.canonicalHeader(cell));
        }
        return headers;
    }

    private static void addRow(List<DataRow> rows, List<String> headers, List<String> cells, int rowNumber) {
        if (cells.stream().allMatch(String::isBlank)) {
            return;
        }
        if (rows.size() >= MAX_ROWS) {
            throw ApiException.badRequest("too_many_rows", "At most " + MAX_ROWS + " rows per file.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            values.putIfAbsent(headers.get(i), i < cells.size() ? cells.get(i) : "");
        }
        rows.add(new DataRow(rowNumber, values));
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        long commas = first.chars().filter(c -> c == ',').count();
        long semicolons = first.chars().filter(c -> c == ';').count();
        long tabs = first.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            // Excel on Windows often exports CSV as Windows-1252.
            return new String(content, java.nio.charset.Charset.forName("windows-1252"));
        }
    }

    private static boolean isZip(byte[] c) {
        return c.length > 4 && c[0] == 'P' && c[1] == 'K' && c[2] == 3 && c[3] == 4;
    }

    private static boolean isOle2(byte[] c) {
        return c.length > 8 && (c[0] & 0xff) == 0xD0 && (c[1] & 0xff) == 0xCF && (c[2] & 0xff) == 0x11
                && (c[3] & 0xff) == 0xE0;
    }
}
