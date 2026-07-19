package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the V18 database boundary directly, after a complete V1..V18 Flyway migration. */
@Testcontainers
class RatificationMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test void enforcesRatificationSnapshotApprovalAndCurrentPointerInvariants() {
        Fixture f = fixture();
        UUID snapshot = snapshot(); UUID packageId = packageFor(f.dealId, f.buyerId, f.sellerId, snapshot, 1, "TRY");
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE ratification_package_snapshot SET content_hash=? WHERE id=?", "b".repeat(64), snapshot));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM ratification_package_snapshot WHERE id=?", snapshot));
        UUID approval = UUID.randomUUID();
        jdbc.update("INSERT INTO ratification_package_approval (id,package_id,legal_entity_id,approved_by_user_id,approved_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", approval, packageId, f.buyerId, f.userId);
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE ratification_package_approval SET approved_at=CURRENT_TIMESTAMP WHERE id=?", approval));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM ratification_package_approval WHERE id=?", approval));
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO ratification_package_approval (id,package_id,legal_entity_id,approved_by_user_id,approved_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), packageId, f.buyerId, f.userId));
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO ratification_package_approval (id,package_id,legal_entity_id,approved_by_user_id,approved_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), packageId, f.outsiderId, f.userId));
        assertThrows(DataAccessException.class, () -> packageFor(f.dealId, f.buyerId, f.sellerId, snapshot(), 0, "TRY"));
        assertThrows(DataAccessException.class, () -> packageFor(f.dealId, f.buyerId, f.sellerId, snapshot(), 9007199254740992L, "TRY"));
        assertThrows(DataAccessException.class, () -> packageFor(f.dealId, f.buyerId, f.sellerId, snapshot(), 1, "try"));
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO ratification_package_snapshot (id,schema_version,canonical_snapshot,content_hash,created_at) VALUES (?,1,'{}',?,CURRENT_TIMESTAMP)", UUID.randomUUID(), "A".repeat(64)));
        UUID badSnapshot = snapshot();
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO ratification_package (id,deal_id,snapshot_id,status,buyer_legal_entity_id,seller_legal_entity_id,amount_minor,currency,created_at) VALUES (?,?,?,'EXPIRED',?,?,1,'TRY',CURRENT_TIMESTAMP)", UUID.randomUUID(), f.dealId, badSnapshot, f.buyerId, f.sellerId));
        UUID otherPackage = packageFor(f.otherDealId, f.buyerId, f.sellerId, snapshot(), 1, "TRY");
        assertDoesNotThrow(() -> jdbc.update("UPDATE deal SET current_ratification_package_id=? WHERE id=?", packageId, f.dealId));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE deal SET current_ratification_package_id=? WHERE id=?", otherPackage, f.dealId));
    }

    static UUID snapshot() { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO ratification_package_snapshot (id,schema_version,canonical_snapshot,content_hash,created_at) VALUES (?,1,'{}',?,CURRENT_TIMESTAMP)", id, "a".repeat(64)); return id; }
    static UUID packageFor(UUID deal, UUID buyer, UUID seller, UUID snapshot, long amount, String currency) { UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO ratification_package (id,deal_id,snapshot_id,status,buyer_legal_entity_id,seller_legal_entity_id,amount_minor,currency,created_at) VALUES (?,?,?,'PENDING',?,?,?, ?,CURRENT_TIMESTAMP)", id,deal,snapshot,buyer,seller,amount,currency); return id; }

    static Fixture fixture() {
        UUID tenant=UUID.randomUUID(), user=UUID.randomUUID(), buyer=UUID.randomUUID(), seller=UUID.randomUUID(), outsider=UUID.randomUUID(), deal=UUID.randomUUID(), other=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user(id,email,password_hash,display_name,enabled) VALUES (?,?, 'x','User',true)",user,user+"@example.com");
        jdbc.update("INSERT INTO tenant(id) VALUES (?)",tenant); jdbc.update("INSERT INTO tenant_user(user_id,tenant_id) VALUES (?,?)",user,tenant);
        for(UUID entity: java.util.List.of(buyer,seller,outsider)) { jdbc.update("INSERT INTO legal_entity(id,tenant_id,legal_name,registration_number) VALUES (?,?,'Entity',?)",entity,tenant,"R"+entity); jdbc.update("INSERT INTO legal_entity_membership(id,tenant_id,legal_entity_id,user_id,role) VALUES (?,?,?,?, 'ADMIN')",UUID.randomUUID(),tenant,entity,user); }
        deal(deal,tenant,buyer,user,"DL-0000000001"); deal(other,tenant,buyer,user,"DL-0000000002");
        for(UUID d: java.util.List.of(deal,other)) for(UUID entity: java.util.List.of(buyer,seller,outsider)) jdbc.update("INSERT INTO deal_participant(deal_id,tenant_id,legal_entity_id,legal_entity_tenant_id) VALUES (?,?,?,?)",d,tenant,entity,tenant);
        return new Fixture(user,buyer,seller,outsider,deal,other);
    }
    static void deal(UUID id,UUID tenant,UUID entity,UUID user,String reference) { jdbc.update("INSERT INTO deal(id,tenant_id,reference,title,deal_status,initiator_legal_entity_id,created_by) VALUES (?,?,?,'Deal','DRAFT',?,?)",id,tenant,reference,entity,user); }
    record Fixture(UUID userId,UUID buyerId,UUID sellerId,UUID outsiderId,UUID dealId,UUID otherDealId) { }
}
