package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of sessions as {@code "AFTERNOON,EVENING"}; empty means "any session". */
@Converter
public class SessionSetConverter implements AttributeConverter<Set<Session>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Session> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(sessions).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<Session> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(Session.class);
        }
        return Arrays.stream(value.split(",")).map(String::strip).map(Session::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Session.class)));
    }
}
