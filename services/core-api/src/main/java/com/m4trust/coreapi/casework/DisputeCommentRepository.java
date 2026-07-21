package com.m4trust.coreapi.casework;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DisputeCommentRepository {

    private final JdbcTemplate jdbcTemplate;

    DisputeCommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(DisputeCommentRecord comment) {
        jdbcTemplate.update("""
                INSERT INTO dispute_comment (
                    id, dispute_case_id, deal_id, body, author_tenant_id, author_legal_entity_id,
                    author_user_id, author_legal_name, author_display_name, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                comment.id(), comment.disputeCaseId(), comment.dealId(), comment.body(),
                comment.authorTenantId(), comment.authorLegalEntityId(), comment.authorUserId(),
                comment.authorLegalName(), comment.authorDisplayName(), Timestamp.from(comment.createdAt()));
    }

    List<DisputeCommentRecord> findByDisputeCaseId(UUID disputeCaseId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_comment
                WHERE dispute_case_id = ?
                ORDER BY created_at ASC, id ASC
                """, this::map, disputeCaseId);
    }

    List<DisputeCommentRecord> findByDisputeCaseIdPage(
            UUID disputeCaseId, long offset, int limit, DisputeCommentQuery.CommentSort sort) {
        String orderBy = sort == DisputeCommentQuery.CommentSort.CREATED_AT_ASC
                ? "created_at ASC, id ASC"
                : "created_at DESC, id DESC";
        return jdbcTemplate.query(
                "SELECT * FROM dispute_comment WHERE dispute_case_id = ? ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                this::map,
                disputeCaseId,
                limit,
                offset);
    }

    long countByDisputeCaseId(UUID disputeCaseId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dispute_comment WHERE dispute_case_id = ?", Long.class, disputeCaseId);
        return count == null ? 0L : count;
    }

    Optional<DisputeCommentRecord> findById(UUID commentId) {
        return jdbcTemplate.query("SELECT * FROM dispute_comment WHERE id = ?", this::map, commentId)
                .stream()
                .findFirst();
    }

    Optional<DisputeCommentRecord> findByIdAndDisputeCaseId(UUID commentId, UUID disputeCaseId) {
        return jdbcTemplate.query(
                "SELECT * FROM dispute_comment WHERE id = ? AND dispute_case_id = ?",
                this::map,
                commentId,
                disputeCaseId).stream().findFirst();
    }

    private DisputeCommentRecord map(ResultSet resultSet, int rowNum) throws SQLException {
        return new DisputeCommentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("dispute_case_id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getString("body"),
                resultSet.getObject("author_tenant_id", UUID.class),
                resultSet.getObject("author_legal_entity_id", UUID.class),
                resultSet.getObject("author_user_id", UUID.class),
                resultSet.getString("author_legal_name"),
                resultSet.getString("author_display_name"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    record DisputeCommentRecord(
            UUID id,
            UUID disputeCaseId,
            UUID dealId,
            String body,
            UUID authorTenantId,
            UUID authorLegalEntityId,
            UUID authorUserId,
            String authorLegalName,
            String authorDisplayName,
            Instant createdAt) {
    }
}
