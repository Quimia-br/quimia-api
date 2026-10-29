package com.api.quimia.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.quimia.TestJwtKeys;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** O teto do pool vem do `application.yml`: sem ele o Hikari abre até 10 conexões e pode estourar o banco. */
@SpringBootTest(classes = com.api.quimia.Application.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class PoolDeConexoesTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void poolTemTetoDeCincoConexoesEMantemUmaOciosa() {
        HikariDataSource pool = (HikariDataSource) dataSource;

        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getMinimumIdle()).isEqualTo(1);
        assertThat(pool.getConnectionTimeout()).isEqualTo(10_000);
        assertThat(pool.getMaxLifetime()).isEqualTo(1_500_000);
    }
}
