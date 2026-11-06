package com.examgrid.backend.audit;

import com.examgrid.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Audit log")
public class AuditController {

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditController(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public record AuditEntry(long id, Instant occurredAt, String actor, String action, String entityType,
                             String entityId, JsonNode details, String requestId) {
    }

    @GetMapping
    @Operation(summary = "Search the audit log, newest first")
    public PageResponse<AuditEntry> search(@RequestParam(required = false) String actor,
                                           @RequestParam(required = false) String action,
                                           @RequestParam(required = false) String entityType,
                                           @RequestParam(required = false) String entityId,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.of(repository.search(actor, action, entityType, entityId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"))),
                a -> new AuditEntry(a.getId(), a.getOccurredAt(), a.getActor(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), mapper.readTree(a.getDetails()), a.getRequestId()));
    }
}
