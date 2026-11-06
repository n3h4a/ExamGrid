package com.examgrid.solver.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReaderTest {

    @Test
    void readsHeaderCaseInsensitivelyAndSkipsBlankLines() throws IOException {
        List<CsvReader.Row> rows = CsvReader.read(new StringReader("﻿Roll,Course\n21CS045,CS301\n\n21CS046,MA201\n"));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("roll")).isEqualTo("21CS045");
        assertThat(rows.get(1).get("COURSE")).isEqualTo("MA201");
        assertThat(rows.get(1).lineNumber()).isEqualTo(4);
    }

    @Test
    void handlesQuotesEscapedQuotesCommasAndCrlf() throws IOException {
        String csv = "code,name\r\nCS301,\"Algorithms, Part \"\"I\"\"\"\r\nMA201,\"Multi\nline\"";
        List<CsvReader.Row> rows = CsvReader.read(new StringReader(csv));
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().get("name")).isEqualTo("Algorithms, Part \"I\"");
        assertThat(rows.get(1).get("name")).isEqualTo("Multi\nline");
    }

    @Test
    void missingTrailingColumnsReadAsEmpty() throws IOException {
        List<CsvReader.Row> rows = CsvReader.read(new StringReader("a,b,c\n1"));
        assertThat(rows.getFirst().get("c")).isEmpty();
        assertThat(rows.getFirst().has("c")).isFalse();
    }

    @Test
    void emptyInputHasNoRows() throws IOException {
        assertThat(CsvReader.read(new StringReader(""))).isEmpty();
    }
}
