package com.examgrid.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Filters are optional; only the ones given become predicates. */
    default Page<AuditLog> search(String actor, String actionPrefix, String entityType, String entityId,
                                  Pageable pageable) {
        Specification<AuditLog> spec = Specification.unrestricted();
        if (actor != null && !actor.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actor"), actor));
        }
        if (actionPrefix != null && !actionPrefix.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.like(root.get("action"), actionPrefix.replace("%", "") + "%"));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        return findAll(spec, pageable);
    }
}
