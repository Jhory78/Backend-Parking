package com.parking.application.usecase;

import com.parking.application.port.in.DatabaseBackupUseCase;
import com.parking.application.port.out.DatabaseBackupPort;
import com.parking.domain.entity.DatabaseBackup;
import com.parking.domain.exception.BackupNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class BackupService implements DatabaseBackupUseCase {

    private final DatabaseBackupPort databaseBackup;

    @Override
    public DatabaseBackup createBackup() {
        return databaseBackup.create();
    }

    @Override
    public Path findBackup(String fileName) {
        return databaseBackup.find(fileName).orElseThrow(BackupNotFoundException::new);
    }

    @Override
    public void restoreBackup(String fileName) {
        databaseBackup.restore(findBackup(fileName));
    }
}
