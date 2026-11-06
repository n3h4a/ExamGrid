package com.examgrid.backend.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One uploaded file and what happened to it. The original bytes are kept in file storage. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    public enum Status { VALIDATED, REJECTED, COMMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportKind kind;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String sha256;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String report;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ImportBatch() {
    }

    public ImportBatch(ImportKind kind, String originalFilename, String contentType, long sizeBytes, String sha256,
                       String storageKey, boolean dryRun, String createdBy, Instant createdAt) {
        this.kind = kind;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.dryRun = dryRun;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.status = Status.VALIDATED;
        this.report = "{}";
    }

    public void complete(Status newStatus, ImportReport r, String reportJson) {
        this.status = newStatus;
        this.totalRows = r.totalRows();
        this.acceptedRows = r.acceptedRows();
        this.errorCount = r.errorCount();
        this.warningCount = r.warningCount();
        this.report = reportJson;
    }

    public Long getId() { return id; }
    public ImportKind getKind() { return kind; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public boolean isDryRun() { return dryRun; }
    public Status getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getAcceptedRows() { return acceptedRows; }
    public int getErrorCount() { return errorCount; }
    public int getWarningCount() { return warningCount; }
    public String getReport() { return report; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
