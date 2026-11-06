package com.examgrid.backend.imports;

import com.examgrid.backend.common.error.ApiException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a file contains, with its columns. Header names are matched loosely: case, spaces,
 * underscores and hyphens are ignored, and common aliases ("Roll No", "Course Code") map to
 * the canonical name.
 */
public enum ImportKind {

    COURSES(List.of("code", "name"), List.of("department", "hard", "sessions"),
            "code,name,department,hard,sessions\nCS301,Design and Analysis of Algorithms,CS,true,\n"
                    + "CS391,Algorithms Laboratory,CS,false,AFTERNOON\n"),
    STUDENTS(List.of("roll"), List.of("name", "department", "email"),
            "roll,name,department,email\n21CS045,Asha Rao,CS,asha@example.edu\n"),
    ENROLMENTS(List.of("roll", "course"), List.of("name", "department"),
            "roll,course,name,department\n21CS045,CS301,Asha Rao,CS\n21CS045,MA201,Asha Rao,CS\n"),
    ROOMS(List.of("code", "rows", "columns"), List.of("building", "blocked", "priority"),
            "code,building,rows,columns,blocked,priority\nR101,Main Block,6,8,,1\nR204,Main Block,8,10,A1;H10,2\n"),
    FACULTY(List.of("code", "name"), List.of("email", "department", "teaches", "unavailable"),
            "code,name,email,department,teaches,unavailable\nF001,Dr Meera Iyer,meera@example.edu,CS,CS301;CS391,"
                    + "2026-12-14\n");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("rollno", "roll"), Map.entry("rollnumber", "roll"), Map.entry("regno", "roll"),
            Map.entry("registrationnumber", "roll"), Map.entry("studentid", "roll"),
            Map.entry("coursecode", "course"), Map.entry("subject", "course"), Map.entry("subjectcode", "course"),
            Map.entry("papercode", "course"),
            Map.entry("coursename", "name"), Map.entry("title", "name"), Map.entry("studentname", "name"),
            Map.entry("dept", "department"), Map.entry("branch", "department"),
            Map.entry("roomcode", "code"), Map.entry("roomno", "code"), Map.entry("staffcode", "code"),
            Map.entry("staffid", "code"), Map.entry("facultycode", "code"),
            Map.entry("cols", "columns"), Map.entry("benchesperrow", "columns"), Map.entry("numrows", "rows"),
            Map.entry("blockedseats", "blocked"), Map.entry("allowedsessions", "sessions"),
            Map.entry("session", "sessions"), Map.entry("difficult", "hard"), Map.entry("away", "unavailable"));

    private final List<String> required;
    private final List<String> optional;
    private final String template;

    ImportKind(List<String> required, List<String> optional, String template) {
        this.required = required;
        this.optional = optional;
        this.template = template;
    }

    public List<String> required() { return required; }
    public List<String> optional() { return optional; }
    public String template() { return template; }

    public static ImportKind fromPath(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Import type", value);
        }
    }

    /** Canonical column name for a header cell. */
    public static String canonicalHeader(String header) {
        String key = header.replace("﻿", "").strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-.]+", "");
        return ALIASES.getOrDefault(key, key);
    }
}
