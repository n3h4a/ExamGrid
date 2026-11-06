package com.examgrid.backend.imports;

import com.examgrid.backend.catalog.CatalogDtos;
import com.examgrid.backend.imports.TabularFileReader.DataRow;
import com.examgrid.backend.imports.TabularFileReader.Table;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Field-level parsing shared by the import handlers; each method reports problems as issues. */
public final class ImportSupport {

    public static final Pattern CODE = Pattern.compile(CatalogDtos.CODE_PATTERN);
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private ImportSupport() {
    }

    /** Adds an error for each required column missing from the header; returns false if any. */
    public static boolean checkHeaders(Table table, ImportKind kind, List<ImportIssue> issues) {
        boolean ok = true;
        for (String column : kind.required()) {
            if (!table.headers().contains(column)) {
                issues.add(ImportIssue.error(1, column, "Missing required column '" + column + "'. Expected columns: "
                        + String.join(", ", kind.required()) + " (optional: " + String.join(", ", kind.optional()) + ")."));
                ok = false;
            }
        }
        return ok;
    }

    public static String code(DataRow row, String column, List<ImportIssue> issues) {
        String raw = row.get(column);
        if (raw.isEmpty()) {
            issues.add(ImportIssue.error(row.rowNumber(), column, "Value is required."));
            return null;
        }
        String code = raw.toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            issues.add(ImportIssue.error(row.rowNumber(), column, "'" + raw
                    + "' is not a valid code (letters, digits, '.', '_' or '-', up to 32 characters)."));
            return null;
        }
        return code;
    }

    public static String required(DataRow row, String column, int max, List<ImportIssue> issues) {
        String value = row.get(column);
        if (value.isEmpty()) {
            issues.add(ImportIssue.error(row.rowNumber(), column, "Value is required."));
            return null;
        }
        return truncate(row, column, value, max, issues);
    }

    public static String optional(DataRow row, String column, int max, List<ImportIssue> issues) {
        String value = row.get(column);
        return value.isEmpty() ? "" : truncate(row, column, value, max, issues);
    }

    public static String email(DataRow row, List<ImportIssue> issues) {
        String value = row.get("email");
        if (value.isEmpty()) {
            return null;
        }
        if (!EMAIL.matcher(value).matches() || value.length() > 254) {
            issues.add(ImportIssue.error(row.rowNumber(), "email", "'" + value + "' is not an email address."));
            return null;
        }
        return value;
    }

    public static Boolean bool(DataRow row, String column, List<ImportIssue> issues) {
        String value = row.get(column).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "false", "no", "n", "0" -> false;
            case "true", "yes", "y", "1" -> true;
            default -> {
                issues.add(ImportIssue.error(row.rowNumber(), column, "'" + row.get(column)
                        + "' is not yes/no or true/false."));
                yield null;
            }
        };
    }

    public static Integer integer(DataRow row, String column, int min, int max, Integer fallback, List<ImportIssue> issues) {
        String value = row.get(column);
        if (value.isEmpty()) {
            if (fallback == null) {
                issues.add(ImportIssue.error(row.rowNumber(), column, "Value is required."));
            }
            return fallback;
        }
        try {
            int n = Integer.parseInt(value);
            if (n < min || n > max) {
                issues.add(ImportIssue.error(row.rowNumber(), column, "Must be between " + min + " and " + max + "."));
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            issues.add(ImportIssue.error(row.rowNumber(), column, "'" + value + "' is not a whole number."));
            return null;
        }
    }

    /** Splits a list cell on ';', '|' or ','. */
    public static List<String> list(DataRow row, String column) {
        String value = row.get(column);
        if (value.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split("[;|,]")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String truncate(DataRow row, String column, String value, int max, List<ImportIssue> issues) {
        if (value.length() > max) {
            issues.add(ImportIssue.error(row.rowNumber(), column, "Longer than " + max + " characters."));
            return null;
        }
        return value;
    }
}
