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
