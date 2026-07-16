/**
 * Owns append-only business audit persistence.
 *
 * <p>Audit records are appended through a narrow port inside the same
 * PostgreSQL transaction as their business mutation. Audit persistence is not
 * an application logging substitute.
 */
package com.m4trust.coreapi.audit;
