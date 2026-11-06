package com.examgrid.backend.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Object storage for raw imports and generated PDFs. Keys are relative, slash-separated paths
 * such as {@code imports/2026/11/<sha>-enrolments.csv}. The local-disk implementation is the
 * default; an S3/MinIO implementation can replace it without touching callers.
 */
public interface FileStorage {

    /** Stores {@code content} under {@code key}, replacing any existing object. */
    StoredObject put(String key, byte[] content, String contentType);

    /** Opens the object for reading, or empty when it does not exist. */
    Optional<InputStream> open(String key);

    boolean exists(String key);

    void delete(String key);

    /** Metadata of a stored object. */
    record StoredObject(String key, long size) {
    }
}
