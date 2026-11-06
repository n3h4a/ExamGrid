package com.examgrid.backend.imports;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.imports.TabularFileReader.Table;
import com.examgrid.backend.security.CurrentUser;
import com.examgrid.backend.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Upload pipeline: archive the original bytes, parse, validate every row, and either report
 * (dry run, or any error) or apply the whole file in one transaction. A file with a single
 * error writes nothing, so the database never holds half an import.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final Map<ImportKind, ImportHandler> handlers = new EnumMap<>(ImportKind.class);
    private final ImportBatchRepository batches;
    private final FileStorage storage;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ImportService(List<ImportHandler> handlers, ImportBatchRepository batches, FileStorage storage,
                         AuditService audit, JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        handlers.forEach(h -> this.handlers.put(h.kind(), h));
        this.batches = batches;
        this.storage = storage;
        this.audit = audit;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Result returned to the client. */
    public record Outcome(ImportBatch batch, ImportReport report) {
    }

    @Transactional
    public Outcome importFile(ImportKind kind, String filename, String contentType, byte[] content, boolean dryRun,
                              ImportMode mode) {
        if (mode == ImportMode.REPLACE && kind != ImportKind.ENROLMENTS) {
            throw ApiException.badRequest("unsupported_mode", "REPLACE mode is only available for enrolments.");
        }
        String safeName = sanitise(filename);
        String sha = sha256(content);
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        String key = String.format("imports/%d/%02d/%s-%s", now.getYear(), now.getMonthValue(), sha.substring(0, 16),
                safeName);
        storage.put(key, content, contentType);

        ImportBatch batch = batches.save(new ImportBatch(kind, safeName, contentType, content.length, sha, key, dryRun,
                CurrentUser.actorName(), clock.instant()));

        Table table;
        try {
            table = TabularFileReader.read(content, filename);
        } catch (ApiException e) {
            ImportReport report = new ImportReport(kind, mode, 0, 0, 1, 0,
                    List.of(ImportIssue.error(1, null, e.getMessage())), false, Map.of());
            return finish(batch, ImportBatch.Status.REJECTED, report);
        }

        // Serialise imports so two uploads cannot interleave their validation and writes.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('examgrid-import'))::text", String.class);
        ImportHandler handler = handlers.get(kind);
        ImportHandler.Validation validation = handler.validate(table, mode);
        ImportReport report = report(kind, mode, table.rows().size(), validation);

        if (report.hasErrors()) {
            return finish(batch, ImportBatch.Status.REJECTED, report);
        }
        if (dryRun) {
            return finish(batch, ImportBatch.Status.VALIDATED, report);
        }
        long started = System.nanoTime();
        handler.commit(validation, mode, batch.getId());
        log.info("Imported {} {} rows from {} in {} ms", report.acceptedRows(), kind, safeName,
                (System.nanoTime() - started) / 1_000_000);
        return finish(batch, ImportBatch.Status.COMMITTED, report);
    }

    private Outcome finish(ImportBatch batch, ImportBatch.Status status, ImportReport report) {
        batch.complete(status, report, mapper.writeValueAsString(report));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", report.kind().name());
        details.put("file", batch.getOriginalFilename());
        details.put("sha256", batch.getSha256());
        details.put("rows", report.totalRows());
        details.put("errors", report.errorCount());
        details.put("changes", report.changes());
        audit.record("import." + status.name().toLowerCase(), "import_batch", batch.getId(), details);
        return new Outcome(batch, report);
    }

    private static ImportReport report(ImportKind kind, ImportMode mode, int totalRows, ImportHandler.Validation v) {
        int errors = (int) v.issues().stream().filter(i -> i.severity() == ImportIssue.Severity.ERROR).count();
        int warnings = v.issues().size() - errors;
        List<ImportIssue> shown = v.issues().stream()
                .sorted((a, b) -> a.severity() != b.severity() ? a.severity().compareTo(b.severity())
                        : Integer.compare(a.row(), b.row()))
                .limit(ImportReport.MAX_ISSUES)
                .toList();
        return new ImportReport(kind, mode, totalRows, errors > 0 ? 0 : v.accepted(), errors, warnings, shown,
                v.issues().size() > ImportReport.MAX_ISSUES, v.changes());
    }

    static String sanitise(String filename) {
        String base = filename == null ? "upload" : filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        base = base.replaceAll("^[._-]+", "");
        if (base.isEmpty()) {
            base = "upload";
        }
        return base.length() > 100 ? base.substring(base.length() - 100) : base;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** CSV template with example rows for a kind. */
    public static byte[] template(ImportKind kind) {
        return kind.template().getBytes(StandardCharsets.UTF_8);
    }
}
