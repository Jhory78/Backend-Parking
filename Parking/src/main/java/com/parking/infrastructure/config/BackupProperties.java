package com.parking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @param directory where the copies are stored.
 * @param command   pg_dump executable. Must be as recent as the database server.
 * @param restoreCommand psql executable.
 * @param timeout   a copy taking longer is cancelled.
 */
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(
        @DefaultValue("backups") Path directory,
        @DefaultValue("pg_dump") String command,
        @DefaultValue("psql") String restoreCommand,
        @DefaultValue("5m") Duration timeout
) {
}
