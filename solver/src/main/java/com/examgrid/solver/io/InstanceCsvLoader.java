package com.examgrid.solver.io;

import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.InvalidInstanceException;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Session;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the CLI's CSV formats into a {@link ProblemInstance.Builder}. List-valued columns
 * use {@code ;} as separator.
 *
 * <pre>
 * enrolments.csv  roll,course
 * courses.csv     code,name,hard,sessions          (sessions e.g. "AFTERNOON")
 * rooms.csv       code,building,rows,columns,blocked   (blocked e.g. "A1;C4")
 * faculty.csv     id,name,teaches,unavailable
 * </pre>
 *
 * Courses that appear only in the enrolment file are created with default settings.
 */
public final class InstanceCsvLoader {

    private InstanceCsvLoader() {
    }

    public static void loadEnrolments(Path file, ProblemInstance.Builder builder, Set<String> knownCourses)
            throws IOException {
        for (CsvReader.Row row : rows(file)) {
            String roll = required(row, "roll", file);
            String course = required(row, "course", file);
            if (knownCourses.add(course)) {
                builder.course(Course.of(course));
            }
            builder.enrol(roll, course);
        }
    }

    public static Set<String> loadCourses(Path file, ProblemInstance.Builder builder) throws IOException {
        Set<String> codes = new LinkedHashSet<>();
        for (CsvReader.Row row : rows(file)) {
            String code = required(row, "code", file);
            Set<Session> sessions = split(row.get("sessions")).stream()
                    .map(s -> Session.valueOf(s.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toSet());
            builder.course(new Course(code, row.get("name"), Boolean.parseBoolean(row.get("hard")), sessions));
            codes.add(code);
        }
        return codes;
    }

    public static void loadRooms(Path file, ProblemInstance.Builder builder) throws IOException {
        for (CsvReader.Row row : rows(file)) {
            Set<SeatPosition> blocked = split(row.get("blocked")).stream()
                    .map(SeatPosition::parse)
                    .collect(Collectors.toSet());
            builder.room(new Room(required(row, "code", file), row.get("building"),
                    integer(row, "rows", file), integer(row, "columns", file), blocked));
        }
    }

    public static void loadFaculty(Path file, ProblemInstance.Builder builder) throws IOException {
        for (CsvReader.Row row : rows(file)) {
            builder.faculty(new Faculty(required(row, "id", file), row.get("name"),
                    Set.copyOf(split(row.get("teaches"))), Set.copyOf(split(row.get("unavailable")))));
        }
    }

    private static List<CsvReader.Row> rows(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return CsvReader.read(reader);
        }
    }

    private static String required(CsvReader.Row row, String column, Path file) {
        if (!row.has(column)) {
            throw new InvalidInstanceException(file.getFileName() + " line " + row.lineNumber()
                    + ": missing value for '" + column + "'");
        }
        return row.get(column);
    }

    private static int integer(CsvReader.Row row, String column, Path file) {
        String raw = required(row, column, file);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new InvalidInstanceException(file.getFileName() + " line " + row.lineNumber()
                    + ": '" + column + "' must be a whole number but was '" + raw + "'");
        }
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
