package com.m4trust.coreapi.deployment;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Minimal, one-shot Flyway process used before a deployed web process starts.
 *
 * <p>This application intentionally does not component-scan the Core API. It
 * creates only the auto-configured datasource and Flyway infrastructure, then
 * closes the context so the process exits after migration. Startup exceptions
 * remain uncaught and therefore produce a non-zero process exit code.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class DatabaseMigrationApplication {

    public static void main(String[] args) {
        migrate(args);
    }

    static void migrate(String... args) {
        SpringApplication application = new SpringApplication(
                DatabaseMigrationApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String[] migrationArguments = Arrays.copyOf(args, args.length + 1);
        migrationArguments[args.length] = "--spring.flyway.enabled=true";

        try (ConfigurableApplicationContext ignored = application.run(migrationArguments)) {
            // Flyway migration completes during context startup.
        }
    }
}
