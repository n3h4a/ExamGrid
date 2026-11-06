package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses slot keys. Accepted forms: {@code 2026-12-01-MORNING}, {@code 2026-12-01 MORNING}
 * and, where a whole day is meant, {@code 2026-12-01} (session empty).
 */
public final class SlotKeys {

    private SlotKeys() {
    }

    /** @param session null when the key names a whole day */
    public record Key(LocalDate date, Session session) {
    }

    public static Optional<Key> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (text.length() < 10) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(text.substring(0, 10));
            String rest = text.substring(10).replaceFirst("^[\\s-]+", "").toUpperCase(Locale.ROOT);
            if (rest.isEmpty()) {
                return Optional.of(new Key(date, null));
            }
            return Optional.of(new Key(date, Session.valueOf(rest)));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
