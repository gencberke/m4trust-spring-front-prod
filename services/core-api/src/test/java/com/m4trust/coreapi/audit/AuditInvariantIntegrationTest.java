package com.m4trust.coreapi.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.m4trust.coreapi.audit.domain.AuditRecord;
import com.m4trust.coreapi.audit.domain.port.AuditAppendPort;
import com.m4trust.coreapi.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class AuditInvariantIntegrationTest extends PostgresIntegrationTestSupport {

  @Autowired private AuditAppendPort auditAppender;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private UUID tenantId;

  @BeforeEach
  void resetDatabase() {
    jdbc.execute("TRUNCATE TABLE audit_record, legal_entity, tenant CASCADE");
    tenantId = UUID.randomUUID();
    jdbc.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
  }

  @Test
  void appendRequiresAnOwningTransaction() {
    assertThrows(
        IllegalTransactionStateException.class,
        () -> auditAppender.append(auditRecord(UUID.randomUUID(), UUID.randomUUID())));
  }

  @Test
  void representativeBusinessMutationAndAuditRollBackAndCommitTogether() {
    UUID rolledBackEntity = UUID.randomUUID();
    transaction()
        .executeWithoutResult(
            status -> {
              insertLegalEntity(rolledBackEntity, "Rollback Entity", "AUDIT-ROLLBACK");
              auditAppender.append(auditRecord(rolledBackEntity, rolledBackEntity));
              status.setRollbackOnly();
            });

    assertEquals(0, count("legal_entity"));
    assertEquals(0, count("audit_record"));

    UUID committedEntity = UUID.randomUUID();
    transaction()
        .executeWithoutResult(
            status -> {
              insertLegalEntity(committedEntity, "Committed Entity", "AUDIT-COMMIT");
              auditAppender.append(auditRecord(committedEntity, committedEntity));
            });

    assertEquals(1, count("legal_entity"));
    assertEquals(1, count("audit_record"));
  }

  @Test
  void databaseRejectsAuditUpdatesAndDeletes() {
    UUID id = UUID.randomUUID();
    transaction().executeWithoutResult(status -> auditAppender.append(auditRecord(id, id)));

    assertThrows(
        RuntimeException.class,
        () -> jdbc.update("UPDATE audit_record SET action = 'changed' WHERE id = ?", id));
    assertThrows(
        RuntimeException.class, () -> jdbc.update("DELETE FROM audit_record WHERE id = ?", id));
  }

  private TransactionTemplate transaction() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transaction;
  }

  private void insertLegalEntity(UUID id, String legalName, String registrationNumber) {
    jdbc.update(
        "INSERT INTO legal_entity (id, tenant_id, legal_name, registration_number) VALUES (?, ?, ?, ?)",
        id,
        tenantId,
        legalName,
        registrationNumber);
  }

  private AuditRecord auditRecord(UUID id, UUID subjectId) {
    return new AuditRecord(
        id,
        tenantId,
        null,
        null,
        "AUDIT_TEST",
        subjectId,
        "AUDIT_TESTED",
        UUID.randomUUID(),
        null,
        Instant.now());
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
