package com.m4trust.coreapi.organization;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class TenantRepository {

    private final JdbcTemplate jdbcTemplate;

    TenantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertTenant(UUID tenantId) {
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
    }

    void linkUser(UUID tenantId, UUID userId) {
        jdbcTemplate.update("""
                INSERT INTO tenant_user (tenant_id, user_id)
                VALUES (?, ?)
                """, tenantId, userId);
    }
}
