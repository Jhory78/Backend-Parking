package com.parking.application.port.in;

import com.parking.domain.entity.DatabaseBackup;

import java.nio.file.Path;

/**
 * Full copies of the database, stored in the configured directory.
 */
public interface DatabaseBackupUseCase {

    /**
     * @throws com.parking.domain.exception.BackupFailedException when the copy cannot be generated.
     */
    DatabaseBackup createBackup();

    /**
     * @return the stored file.
     * @throws com.parking.domain.exception.BackupNotFoundException when no copy has that name.
     */
    Path findBackup(String fileName);

    /**
     * RF-55: replaces the current data with a stored copy.
     *
     * @throws com.parking.domain.exception.BackupNotFoundException when no copy has that name.
     * @throws com.parking.domain.exception.BackupFailedException   when the copy cannot be applied.
     */
    void restoreBackup(String fileName);
}
