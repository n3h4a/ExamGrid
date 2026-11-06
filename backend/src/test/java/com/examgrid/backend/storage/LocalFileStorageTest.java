package com.examgrid.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletes() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        storage.put("imports/2026/11/abc-file.csv", "a,b".getBytes(StandardCharsets.UTF_8), "text/csv");
        assertThat(storage.exists("imports/2026/11/abc-file.csv")).isTrue();
        try (InputStream in = storage.open("imports/2026/11/abc-file.csv").orElseThrow()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("a,b");
        }
        storage.delete("imports/2026/11/abc-file.csv");
        assertThat(storage.open("imports/2026/11/abc-file.csv")).isEmpty();
    }

    @Test
    void rejectsKeysThatCouldEscapeTheRoot() {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        for (String key : new String[] {"../secret", "/etc/passwd", "a/../../b", "a//b", ".hidden", "a\\b"}) {
            assertThatThrownBy(() -> storage.put(key, new byte[1], null))
                    .as(key).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
