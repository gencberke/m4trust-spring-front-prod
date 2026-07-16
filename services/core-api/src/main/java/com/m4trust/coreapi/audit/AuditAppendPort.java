package com.m4trust.coreapi.audit;

/**
 * Audit-owned entry point for appending immutable business audit records in
 * the caller's active transaction.
 */
public interface AuditAppendPort {

    void append(AuditRecord record);
}
