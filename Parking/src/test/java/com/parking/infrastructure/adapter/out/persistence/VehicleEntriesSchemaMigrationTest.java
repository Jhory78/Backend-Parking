package com.parking.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the database rules of V4, which protect the data even if the
 * application has a bug or two requests run at the same time.
 * <p>
 * Flyway and the test share one connection: H2 evaluates CHECK constraints with
 * the session that created them and fails with "database closed" once it is gone.
 */
class VehicleEntriesSchemaMigrationTest {

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrate() {
        String url = "jdbc:h2:mem:entries_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void close() {
        dataSource.destroy();
    }

    @Test
    void plateCannotHaveTwoActiveEntries() {
        insertActiveEntry("ABC123", "C-01");

        assertThatThrownBy(() -> insertActiveEntry("ABC123", "C-02"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void closedEntriesDoNotBlockANewEntry() {
        insertClosedEntry("ABC123", "C-01");
        insertClosedEntry("ABC123", "C-02");

        assertThatCode(() -> insertActiveEntry("ABC123", "C-03")).doesNotThrowAnyException();
    }

    @Test
    void activeEntryMustCarryItsPlateAndNoExitTime() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO vehicle_entries (plate, vehicle_type, space_id, entry_time, status, active_plate)
                VALUES ('ABC123', 'CAR', ?, CURRENT_TIMESTAMP, 'ACTIVE', NULL)
                """, spaceId("C-01")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void freeSpaceCannotHaveAPlate() {
        assertThatThrownBy(() -> jdbc.update("UPDATE parking_spaces SET current_plate = 'ABC123' WHERE code = 'C-01'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertActiveEntry(String plate, String spaceCode) {
        jdbc.update("""
                INSERT INTO vehicle_entries (plate, vehicle_type, space_id, entry_time, status, active_plate)
                VALUES (?, 'CAR', ?, CURRENT_TIMESTAMP, 'ACTIVE', ?)
                """, plate, spaceId(spaceCode), plate);
    }

    private void insertClosedEntry(String plate, String spaceCode) {
        jdbc.update("""
                INSERT INTO vehicle_entries (plate, vehicle_type, space_id, entry_time, exit_time, status)
                VALUES (?, 'CAR', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'CLOSED')
                """, plate, spaceId(spaceCode));
    }

    private Long spaceId(String code) {
        return jdbc.queryForObject("SELECT id FROM parking_spaces WHERE code = ?", Long.class, code);
    }
}
