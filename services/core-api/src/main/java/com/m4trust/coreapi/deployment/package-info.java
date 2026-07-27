/**
 * Spring Boot entry point for database migration and other operational one-shot tasks.
 *
 * <p>This package intentionally has no {@code api}, {@code domain}, or {@code infra} split: it
 * contains a single {@link com.m4trust.coreapi.deployment.DatabaseMigrationApplication} class and
 * is excluded from layered-module ArchUnit rules alongside {@code sharedkernel}.
 */
package com.m4trust.coreapi.deployment;
