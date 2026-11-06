package com.examgrid.backend.support;

import java.util.concurrent.ThreadLocalRandom;

/** Unique, pattern-conforming identifiers so integration tests never collide on shared data. */
public final class TestData {

    private TestData() {
    }

    /** Four random letters, e.g. {@code QKZT}. */
    public static String letters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return sb.toString();
    }

    /** A roll number matching the default pattern, unique per {@code dept}. */
    public static String roll(String dept, int n) {
        return String.format("21%s%03d", dept, n);
    }
}
