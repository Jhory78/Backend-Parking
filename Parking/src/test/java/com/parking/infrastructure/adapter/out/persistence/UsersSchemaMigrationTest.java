package com.parking.infrastructure.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that V2 translates a Sprint 1 database (Spanish columns) to English
 * without losing data.
 */
class UsersSchemaMigrationTest {

    private String jdbcUrl;

    @BeforeEach
    void createIsolatedDatabase() {
        jdbcUrl = "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    }

    @Test
    void translatesSprintOneDataToEnglish() throws SQLException {
        migrateTo("1");
        execute("""
                INSERT INTO users (nombre, correo, password, rol, activo) VALUES
                    ('Administrador', 'admin@parking.com', 'hash-1', 'ADMIN', TRUE),
                    ('Juan Perez', 'juan@parking.com', 'hash-2', 'OPERARIO', FALSE)
                """);

        migrateTo("latest");

        Map<String, String> rowsByEmail = queryUsers();
        assertThat(rowsByEmail).containsExactly(
                Map.entry("admin@parking.com", "Administrador|hash-1|ADMIN|ACTIVE"),
                Map.entry("juan@parking.com", "Juan Perez|hash-2|OPERATOR|INACTIVE"));
    }

    @Test
    void rejectsUnknownRoleAndStatusAfterMigration() throws SQLException {
        migrateTo("latest");

        assertThatThrownBy(() -> execute("""
                INSERT INTO users (name, email, password, role, status)
                VALUES ('X', 'x@parking.com', 'hash', 'OPERARIO', 'ACTIVE')
                """)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("""
                INSERT INTO users (name, email, password, role, status)
                VALUES ('X', 'x@parking.com', 'hash', 'OPERATOR', 'ACTIVO')
                """)).isInstanceOf(SQLException.class);
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .target(target)
                .load()
                .migrate();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Map<String, String> queryUsers() throws SQLException {
        Map<String, String> rowsByEmail = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name, email, password, role, status FROM users ORDER BY id")) {
            while (rows.next()) {
                rowsByEmail.put(rows.getString("email"), String.join("|",
                        rows.getString("name"), rows.getString("password"),
                        rows.getString("role"), rows.getString("status")));
            }
        }
        return rowsByEmail;
    }
}
