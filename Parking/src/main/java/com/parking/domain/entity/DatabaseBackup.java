package com.parking.domain.entity;

import java.time.Instant;

/**
 * A full copy of the database, stored in the configured directory.
 *
 * @param fileName  name of the file inside that directory.
 * @param sizeBytes size of the file.
 * @param createdAt when the copy finished.
 */
public record DatabaseBackup(String fileName, long sizeBytes, Instant createdAt) {
}
