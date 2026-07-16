package com.m4trust.coreapi.audit;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditRepository {

    private final JdbcTemplate jdbcTemplate;

    AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(AuditRecord record) {
        jdbcTemplate.update("""
                INSERT INTO audit_record (
                    id,
                    tenant_id,
                    actor_user_id,
                    legal_entity_id,
                    subject_type,
                    subject_id,
                    action,
                    correlation_id,
                    causation_id,
                    occurred_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(),
                record.tenantId(),
                record.actorUserId(),
                record.legalEntityId(),
                record.subjectType(),
                record.subjectId(),
                record.action(),
                record.correlationId(),
                record.causationId(),
                Timestamp.from(record.occurredAt()));
    }
}
