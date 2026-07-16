package com.m4trust.coreapi.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public IdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate =
                new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    void insert(IdentityAccount account) {
        jdbcTemplate.update("""
                INSERT INTO identity_user
                    (id, email, password_hash, display_name, enabled)
                VALUES (?, ?, ?, ?, ?)
                """,
                account.id(), account.email(), account.passwordHash(),
                account.displayName(), account.enabled());
    }

    Optional<IdentityAccount> findByNormalizedEmail(String normalizedEmail) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash, display_name, enabled
                        FROM identity_user
                        WHERE email = ?
                        """,
                this::mapAccount, normalizedEmail).stream().findFirst();
    }

    List<PublicIdentityProjection> findPublicProjections(
            Collection<UUID> userIds) {
        return namedParameterJdbcTemplate.query("""
                SELECT id, email, display_name
                FROM identity_user
                WHERE id IN (:userIds)
                """, Map.of("userIds", userIds),
                (resultSet, rowNumber) -> new PublicIdentityProjection(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("email"),
                        resultSet.getString("display_name")));
    }

    private IdentityAccount mapAccount(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new IdentityAccount(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("enabled"));
    }

    record PublicIdentityProjection(
            UUID id, String email, String displayName) {
    }
}
