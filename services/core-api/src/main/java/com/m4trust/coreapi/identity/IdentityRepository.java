package com.m4trust.coreapi.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    private IdentityAccount mapAccount(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new IdentityAccount(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getBoolean("enabled"));
    }
}
