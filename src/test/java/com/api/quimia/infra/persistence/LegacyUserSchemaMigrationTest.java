package com.api.quimia.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class LegacyUserSchemaMigrationTest {
    @Test
    void versionSevenMigrationRestoresMissingBlockedUntilColumnAfterVersionSix() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:missing_blocked_until_migration;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE usuario (id UUID PRIMARY KEY, falhas_login INTEGER NOT NULL DEFAULT 0)");
            statement.execute(
                    "INSERT INTO usuario (id, falhas_login) VALUES ('00000000-0000-0000-0000-000000000001', 3)");
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("6")
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection();
                ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "usuario", "bloqueado_ate")) {
            assertTrue(columns.next());
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT falhas_login FROM usuario WHERE id = '00000000-0000-0000-0000-000000000001'")) {
            assertTrue(result.next());
            assertEquals(3, result.getInt("falhas_login"));
        }
    }

    @Test
    void migrationAllowsRegistrationWithoutOverwritingLegacyPasswordValues() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:legacy_user_migration;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE usuario (id INTEGER PRIMARY KEY, senha VARCHAR(255) NOT NULL, senha_hash VARCHAR(255))");
            statement.execute("INSERT INTO usuario (id, senha) VALUES (1, 'legacy-value')");
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("5")
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.executeUpdate(
                    "INSERT INTO usuario (id, senha_hash) VALUES (2, '{bcrypt}encoded')"));

            try (ResultSet result = statement.executeQuery("SELECT senha FROM usuario WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals("legacy-value", result.getString("senha"));
            }
        }
    }
}
