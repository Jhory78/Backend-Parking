package com.parking.application.port.out;

import com.parking.domain.entity.DatabaseBackup;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Tool that copies the database and stores the result.
 */
public interface DatabaseBackupPort {

    /**
     * @throws com.parking.domain.exception.BackupFailedException when the copy cannot be generated.
     */
    DatabaseBackup create();

    Optional<Path> find(String fileName);

    /**
     * Replaces the current data with the copy. If it fails, the data stays as it was.
     *
     * @throws com.parking.domain.exception.BackupFailedException when the copy cannot be applied.
     */
    void restore(Path backup);
}
