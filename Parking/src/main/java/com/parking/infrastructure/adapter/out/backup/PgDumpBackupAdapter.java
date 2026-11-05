package com.parking.infrastructure.adapter.out.backup;

import com.parking.application.port.out.DatabaseBackupPort;
import com.parking.domain.entity.DatabaseBackup;
import com.parking.domain.exception.BackupFailedException;
import com.parking.infrastructure.config.BackupProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Copies the database with pg_dump and restores it with psql. The password travels in the process environment,
 * never in its arguments, and the output of the tool is only logged.
 */
@Slf4j
@Component
public class PgDumpBackupAdapter implements DatabaseBackupPort {

    private static final Pattern FILE_NAME = Pattern.compile("parking-backup-\\d{8}-\\d{6}\\.sql");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final BackupProperties properties;
    private final Clock clock;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PgDumpBackupAdapter(BackupProperties properties,
                               Clock clock,
                               @Value("${spring.datasource.url}") String jdbcUrl,
                               @Value("${spring.datasource.username:}") String username,
                               @Value("${spring.datasource.password:}") String password) {
        this.properties = properties;
        this.clock = clock;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public DatabaseBackup create() {
        ConnectionSettings connection = ConnectionSettings.parse(jdbcUrl, username, password);
        Path directory = properties.directory().toAbsolutePath();
        Path target = directory.resolve("parking-backup-%s.sql".formatted(TIMESTAMP.format(clock.instant())));
        Path partial = directory.resolve(target.getFileName() + ".partial");
        Path toolOutput = directory.resolve(target.getFileName() + ".log");

        try {
            Files.createDirectories(directory);
            runPgDump(connection, partial, toolOutput);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            return new DatabaseBackup(target.getFileName().toString(), Files.size(target), clock.instant());
        } catch (IOException e) {
            throw new BackupFailedException("Could not run pg_dump or store its result", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupFailedException("Interrupted while running pg_dump", e);
        } finally {
            deleteQuietly(partial);
            deleteQuietly(toolOutput);
        }
    }

    @Override
    public Optional<Path> find(String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        return Optional.of(properties.directory().toAbsolutePath().resolve(fileName)).filter(Files::isRegularFile);
    }

    /** Runs in a single transaction: if any statement fails, the database stays as it was. */
    @Override
    public void restore(Path backup) {
        ConnectionSettings connection = ConnectionSettings.parse(jdbcUrl, username, password);
        Path toolOutput = backup.resolveSibling(backup.getFileName() + ".restore.log");
        List<String> command = new ArrayList<>(List.of(properties.restoreCommand(),
                "--host", connection.host(),
                "--port", String.valueOf(connection.port()),
                "--dbname", connection.database(),
                "--single-transaction",
                "--set", "ON_ERROR_STOP=1",
                "--no-password",
                "--file", backup.toString()));
        if (!connection.username().isEmpty()) {
            command.addAll(List.of("--username", connection.username()));
        }

        try {
            run(command, connection, toolOutput);
        } catch (IOException e) {
            throw new BackupFailedException("Could not run psql", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupFailedException("Interrupted while running psql", e);
        } finally {
            deleteQuietly(toolOutput);
        }
    }

    private void runPgDump(ConnectionSettings connection, Path output, Path toolOutput)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(properties.command(),
                "--host", connection.host(),
                "--port", String.valueOf(connection.port()),
                "--dbname", connection.database(),
                "--table", "public.*",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "--no-password",
                "--file", output.toString()));
        if (!connection.username().isEmpty()) {
            command.addAll(List.of("--username", connection.username()));
        }

        run(command, connection, toolOutput);
    }

    private void run(List<String> command, ConnectionSettings connection, Path toolOutput)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(toolOutput.toFile());
        builder.environment().put("PGPASSWORD", connection.password());
        Process process = builder.start();

        if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly().waitFor();
            throw new BackupFailedException(command.get(0) + " timed out after " + properties.timeout());
        }
        if (process.exitValue() != 0) {
            throw new BackupFailedException("%s exited with %d: %s"
                    .formatted(command.get(0), process.exitValue(), Files.readString(toolOutput)));
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete {}", file, e);
        }
    }

    /** Connection data for pg_dump. Credentials may come in the JDBC URL or as separate properties. */
    record ConnectionSettings(String host, int port, String database, String username, String password) {

        static ConnectionSettings parse(String jdbcUrl, String username, String password) {
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new BackupFailedException("Only PostgreSQL can be backed up");
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            if (uri.getHost() == null) {
                throw new BackupFailedException("The datasource URL has no host");
            }
            Map<String, String> query = parseQuery(uri.getRawQuery());
            return new ConnectionSettings(
                    uri.getHost(),
                    uri.getPort() == -1 ? 5432 : uri.getPort(),
                    uri.getPath().substring(1),
                    username.isEmpty() ? query.getOrDefault("user", "") : username,
                    password.isEmpty() ? query.getOrDefault("password", "") : password);
        }

        private static Map<String, String> parseQuery(String rawQuery) {
            Map<String, String> values = new HashMap<>();
            if (rawQuery != null) {
                for (String pair : rawQuery.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2) {
                        values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                    }
                }
            }
            return values;
        }
    }
}
