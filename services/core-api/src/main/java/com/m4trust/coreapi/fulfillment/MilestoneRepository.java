package com.m4trust.coreapi.fulfillment;

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
class MilestoneRepository {

    private final JdbcTemplate jdbcTemplate;

    MilestoneRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(Milestone.MilestoneRecord milestone, List<Milestone.MilestoneRuleReferenceRecord> ruleReferences) {
        jdbcTemplate.update("""
                INSERT INTO fulfillment_milestone (
                    id, fulfillment_id, deal_id, title, description, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, milestone.id(), milestone.fulfillmentId(), milestone.dealId(),
                milestone.title(), milestone.description(), milestone.status().name(),
                milestone.version(), Timestamp.from(milestone.createdAt()),
                Timestamp.from(milestone.updatedAt()));
        for (Milestone.MilestoneRuleReferenceRecord reference : ruleReferences) {
            jdbcTemplate.update("""
                    INSERT INTO fulfillment_milestone_rule_reference (
                        milestone_id, rule_reference, category
                    ) VALUES (?, ?, ?)
                    """, reference.milestoneId(), reference.ruleReference(), reference.category());
        }
    }

    Optional<Milestone.MilestoneRecord> findByFulfillmentId(UUID fulfillmentId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment_milestone WHERE fulfillment_id = ?",
                this::mapMilestone, fulfillmentId).stream().findFirst();
    }

    Optional<Milestone.MilestoneRecord> findByFulfillmentIdForUpdate(UUID fulfillmentId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment_milestone WHERE fulfillment_id = ? FOR UPDATE",
                this::mapMilestone, fulfillmentId).stream().findFirst();
    }

    List<Milestone.MilestoneRuleReferenceRecord> findRuleReferencesByMilestoneId(UUID milestoneId) {
        return jdbcTemplate.query("""
                SELECT milestone_id, rule_reference, category
                FROM fulfillment_milestone_rule_reference
                WHERE milestone_id = ?
                ORDER BY rule_reference
                """, this::mapRuleReference, milestoneId);
    }

    boolean update(Milestone.MilestoneRecord milestone, long previousVersion) {
        return jdbcTemplate.update("""
                UPDATE fulfillment_milestone
                SET status = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """, milestone.status().name(),
                Timestamp.from(milestone.updatedAt()), milestone.version(),
                milestone.id(), previousVersion) == 1;
    }

    private Milestone.MilestoneRecord mapMilestone(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new Milestone.MilestoneRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("fulfillment_id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getString("title"),
                resultSet.getString("description"),
                FulfillmentStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private Milestone.MilestoneRuleReferenceRecord mapRuleReference(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new Milestone.MilestoneRuleReferenceRecord(
                resultSet.getObject("milestone_id", UUID.class),
                resultSet.getString("rule_reference"),
                resultSet.getString("category"));
    }
}
