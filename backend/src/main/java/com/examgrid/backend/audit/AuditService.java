package com.examgrid.backend.audit;

import com.examgrid.backend.common.web.RequestIdFilter;
import com.examgrid.backend.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;

/**
 * Writes audit entries. Calls join the caller's transaction, so an audited change and its log
 * entry commit or roll back together. {@link #recordIndependently} is for events that must be
 * kept even when the surrounding work fails, such as rejected logins.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        save(CurrentUser.actorName(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(String actor, String action, String entityType, Object entityId,
                                    Map<String, ?> details) {
        save(actor, action, entityType, entityId, details);
    }

    private void save(String actor, String action, String entityType, Object entityId, Map<String, ?> details) {
        String json = mapper.writeValueAsString(details == null ? Map.of() : details);
        repository.save(new AuditLog(clock.instant(), truncate(actor), action, entityType,
                entityId == null ? null : String.valueOf(entityId), json, MDC.get(RequestIdFilter.MDC_KEY)));
        log.info("audit action={} entity={}:{} actor={}", action, entityType, entityId, actor);
    }

    private static String truncate(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
