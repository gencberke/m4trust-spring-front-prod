package com.m4trust.coreapi.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One database lifecycle for the integration-test JVM.
 *
 * <p>Surefire runs this module sequentially. Individual test classes still truncate the tables they
 * own in {@code @BeforeEach}; keeping the container alive avoids paying PostgreSQL start-up and
 * shutdown for every Spring context.
 */
public abstract class PostgresIntegrationTestSupport {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17.5-alpine");

  protected static PostgreSQLContainer<?> postgres() {
    if (!POSTGRES.isRunning()) {
      synchronized (POSTGRES) {
        if (!POSTGRES.isRunning()) {
          POSTGRES.start();
        }
      }
    }
    return POSTGRES;
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> postgres().getUsername());
    registry.add("spring.datasource.password", () -> postgres().getPassword());
  }
}
