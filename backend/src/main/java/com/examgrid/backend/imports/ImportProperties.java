package com.examgrid.backend.imports;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code examgrid.imports.*}.
 *
 * @param rollPattern regular expression every roll number must match after upper-casing,
 *                    e.g. {@code 21CS045}
 */
@ConfigurationProperties("examgrid.imports")
public record ImportProperties(String rollPattern) {

    public ImportProperties {
        rollPattern = rollPattern == null || rollPattern.isBlank() ? "[0-9]{2}[A-Z]{2,4}[0-9]{3}" : rollPattern;
    }
}
