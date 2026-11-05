package com.parking.infrastructure.adapter.in.rest.controller;

import com.parking.application.port.in.DatabaseBackupUseCase;
import com.parking.domain.entity.DatabaseBackup;
import com.parking.infrastructure.adapter.in.rest.dto.BackupResponse;
import com.parking.infrastructure.utils.constants.ApiRoutes;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * Database backups. Only administrators can use them.
 */
@RestController
@RequestMapping(ApiRoutes.BACKUPS)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BackupController {

    private final DatabaseBackupUseCase databaseBackupUseCase;

    /** RF-54: generates a full copy and stores it in the configured directory. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BackupResponse createBackup() {
        DatabaseBackup backup = databaseBackupUseCase.createBackup();
        return new BackupResponse(backup.fileName(), backup.sizeBytes(), backup.createdAt());
    }

    /** RF-55: replaces the current data with a stored copy. */
    @PostMapping(ApiRoutes.BACKUP_RESTORE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreBackup(@PathVariable String fileName) {
        databaseBackupUseCase.restoreBackup(fileName);
    }

    /** Downloads a stored copy. */
    @GetMapping(ApiRoutes.BACKUP_BY_NAME)
    public ResponseEntity<Resource> downloadBackup(@PathVariable String fileName) {
        Path file = databaseBackupUseCase.findBackup(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(new FileSystemResource(file));
    }
}
