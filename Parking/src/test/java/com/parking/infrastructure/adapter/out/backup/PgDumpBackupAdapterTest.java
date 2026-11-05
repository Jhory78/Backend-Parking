package com.parking.infrastructure.adapter.out.backup;

import com.parking.domain.exception.BackupFailedException;
import com.parking.infrastructure.adapter.out.backup.PgDumpBackupAdapter.ConnectionSettings;
import com.parking.infrastructure.config.BackupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgDumpBackupAdapterTest {

    @TempDir
    Path directory;

    private PgDumpBackupAdapter adapter(String command) {
        return new PgDumpBackupAdapter(new BackupProperties(directory, command, command, Duration.ofSeconds(5)),
                Clock.systemUTC(), "jdbc:postgresql://localhost:5432/parking", "user", "secret");
    }

    @Test
    void readsCredentialsFromTheUrlWhenTheyAreNotSetApart() {
        ConnectionSettings settings = ConnectionSettings.parse(
                "jdbc:postgresql://db.example.com:6543/postgres?user=admin&password=a%2Fb%40c", "", "");

        assertThat(settings).isEqualTo(new ConnectionSettings("db.example.com", 6543, "postgres", "admin", "a/b@c"));
    }

    @Test
    void separatePropertiesWinAndPortDefaultsTo5432() {
        ConnectionSettings settings = ConnectionSettings.parse(
                "jdbc:postgresql://db.example.com/postgres?user=admin&password=old", "other", "new");

        assertThat(settings).isEqualTo(new ConnectionSettings("db.example.com", 5432, "postgres", "other", "new"));
    }

    @Test
    void rejectsOtherDatabases() {
        assertThatThrownBy(() -> ConnectionSettings.parse("jdbc:h2:mem:test", "", ""))
                .isInstanceOf(BackupFailedException.class);
    }

    @Test
    void aMissingToolFailsAndLeavesNothingBehind() throws IOException {
        assertThatThrownBy(() -> adapter("pg_dump-that-does-not-exist").create())
                .isInstanceOf(BackupFailedException.class);

        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void aFailedRestoreKeepsTheCopyAndLeavesNothingElse() throws IOException {
        Path backup = Files.writeString(directory.resolve("parking-backup-20261104-193000.sql"), "dump");

        assertThatThrownBy(() -> adapter("psql-that-does-not-exist").restore(backup))
                .isInstanceOf(BackupFailedException.class);

        try (var files = Files.list(directory)) {
            assertThat(files).containsExactly(backup);
        }
    }

    @Test
    void onlyFindsFilesWithTheBackupNameFormat() throws IOException {
        Files.writeString(directory.resolve("parking-backup-20261104-193000.sql"), "dump");
        Files.writeString(directory.resolve("other.sql"), "secret");

        assertThat(adapter("pg_dump").find("parking-backup-20261104-193000.sql")).isPresent();
        assertThat(adapter("pg_dump").find("parking-backup-20261104-193001.sql")).isEmpty();
        assertThat(adapter("pg_dump").find("other.sql")).isEmpty();
        assertThat(adapter("pg_dump").find("../parking-backup-20261104-193000.sql")).isEmpty();
    }
}
