package com.m4trust.coreapi.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.m4trust.coreapi.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseMigrationApplicationTest extends PostgresIntegrationTestSupport {

  @Test
  void oneShotCommandMigratesAndExitsCleanlyWhenRepeated() {
    migrate();
    migrate();

    JdbcTemplate jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                postgres().getJdbcUrl(), postgres().getUsername(), postgres().getPassword()));

    Integer successfulMigrations =
        jdbcTemplate.queryForObject(
            """
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = true
                """,
            Integer.class);
    Integer failedMigrations =
        jdbcTemplate.queryForObject(
            """
                SELECT count(*)
                FROM flyway_schema_history
                WHERE success = false
                """,
            Integer.class);

    assertTrue(successfulMigrations != null && successfulMigrations > 0);
    assertEquals(0, failedMigrations);
  }

  private void migrate() {
    DatabaseMigrationApplication.migrate(
        "--spring.datasource.url=" + postgres().getJdbcUrl(),
        "--spring.datasource.username=" + postgres().getUsername(),
        "--spring.datasource.password=" + postgres().getPassword(),
        "--spring.main.banner-mode=off");
  }
}
