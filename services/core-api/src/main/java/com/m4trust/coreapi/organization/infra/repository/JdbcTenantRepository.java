package com.m4trust.coreapi.organization.infra.repository;

import com.m4trust.coreapi.organization.domain.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTenantRepository implements TenantRepository {

  private final JdbcTemplate jdbcTemplate;

  JdbcTenantRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void insertTenant(UUID tenantId) {
    jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
  }

  @Override
  public void linkUser(UUID tenantId, UUID userId) {
    jdbcTemplate.update(
        """
                INSERT INTO tenant_user (tenant_id, user_id)
                VALUES (?, ?)
                """,
        tenantId,
        userId);
  }
}
