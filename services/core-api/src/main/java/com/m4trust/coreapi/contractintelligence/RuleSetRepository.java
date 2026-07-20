package com.m4trust.coreapi.contractintelligence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Deliberately read/insert-only: accepted history has no mutation API. */
@Repository
class RuleSetRepository {
    private final JdbcTemplate jdbc;
    RuleSetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    void insert(UUID id, UUID dealId, long version, UUID analysisId, UUID extractionId,
            UUID actor, Instant createdAt, UUID previousId, String rules, String excluded) {
        jdbc.update("INSERT INTO contract_intelligence_rule_set_version (id,deal_id,version,source_analysis_id,source_extraction_result_version_id,created_by_user_id,created_at,previous_rule_set_version_id,previous_rule_set_deal_id,rules,excluded_rule_references) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))",
                id, dealId, version, analysisId, extractionId, actor, Timestamp.from(createdAt), previousId, previousId == null ? null : dealId, rules, excluded);
    }
    Optional<Row> find(UUID dealId, UUID id) { return jdbc.query("SELECT id,deal_id,version,source_analysis_id,source_extraction_result_version_id,created_by_user_id,created_at,previous_rule_set_version_id,rules::text,excluded_rule_references::text FROM contract_intelligence_rule_set_version WHERE deal_id=? AND id=?", (r,n)->row(r), dealId,id).stream().findFirst(); }
    Optional<Row> findAny(UUID id) { return jdbc.query("SELECT id,deal_id,version,source_analysis_id,source_extraction_result_version_id,created_by_user_id,created_at,previous_rule_set_version_id,rules::text,excluded_rule_references::text FROM contract_intelligence_rule_set_version WHERE id=?", (r,n)->row(r), id).stream().findFirst(); }
    List<Row> list(UUID dealId) { return jdbc.query("SELECT id,deal_id,version,source_analysis_id,source_extraction_result_version_id,created_by_user_id,created_at,previous_rule_set_version_id,rules::text,excluded_rule_references::text FROM contract_intelligence_rule_set_version WHERE deal_id=? ORDER BY version", (r,n)->row(r),dealId); }
    Optional<Row> latest(UUID dealId) { return jdbc.query("SELECT id,deal_id,version,source_analysis_id,source_extraction_result_version_id,created_by_user_id,created_at,previous_rule_set_version_id,rules::text,excluded_rule_references::text FROM contract_intelligence_rule_set_version WHERE deal_id=? ORDER BY version DESC LIMIT 1", (r,n)->row(r),dealId).stream().findFirst(); }
    private static Row row(java.sql.ResultSet r) throws java.sql.SQLException { return new Row(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3),r.getObject(4,UUID.class),r.getObject(5,UUID.class),r.getObject(6,UUID.class),r.getTimestamp(7).toInstant(),r.getObject(8,UUID.class),r.getString(9),r.getString(10)); }
    record Row(UUID id,UUID dealId,long version,UUID analysisId,UUID extractionId,UUID createdBy,Instant createdAt,UUID previousId,String rules,String excluded) {}
}
