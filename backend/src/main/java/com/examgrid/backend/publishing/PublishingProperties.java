package com.examgrid.backend.publishing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code examgrid.publishing.*}.
 *
 * @param requiredApprovals distinct exam-cell approvals needed to publish (two-person rule)
 * @param institutionName   printed on hall tickets and charts
 */
@ConfigurationProperties("examgrid.publishing")
public record PublishingProperties(Integer requiredApprovals, String institutionName) {

    public PublishingProperties {
        requiredApprovals = requiredApprovals == null || requiredApprovals < 1 ? 2 : requiredApprovals;
        institutionName = institutionName == null || institutionName.isBlank()
                ? "ExamGrid Demo University" : institutionName;
    }
}
