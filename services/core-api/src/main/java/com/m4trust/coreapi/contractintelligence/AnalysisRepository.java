package com.m4trust.coreapi.contractintelligence;
import java.sql.*; import java.time.Instant; import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository;
@Repository class AnalysisRepository {
 private final JdbcTemplate jdbc; AnalysisRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 Optional<Row> latest(UUID documentId){return jdbc.query("SELECT * FROM contract_intelligence_analysis_job WHERE document_id=? ORDER BY requested_at DESC LIMIT 1",this::map,documentId).stream().findFirst();}
 Optional<Row> latestForJob(UUID id){return jdbc.query("SELECT * FROM contract_intelligence_analysis_job WHERE id=?",this::map,id).stream().findFirst();}
 boolean active(UUID documentId){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contract_intelligence_analysis_job WHERE document_id=? AND status IN ('QUEUED','PROCESSING'))",Boolean.class,documentId));}
 void insert(Row r){jdbc.update("INSERT INTO contract_intelligence_analysis_job (id,tenant_id,deal_id,document_id,object_version,input_sha256,status,requested_at,version) VALUES (?,?,?,?,?,?,?,?,?)",r.id,r.tenantId,r.dealId,r.documentId,r.objectVersion,r.sha256,r.status,Timestamp.from(r.requestedAt),r.version);}
 private Row map(ResultSet r,int n)throws SQLException{return new Row(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getObject("deal_id",UUID.class),r.getObject("document_id",UUID.class),r.getString("object_version"),r.getString("input_sha256"),r.getString("status"),r.getTimestamp("requested_at").toInstant(),instant(r,"processing_started_at"),instant(r,"completed_at"),instant(r,"failed_at"),r.getString("failure_code"),r.getObject("retry_recommended",Boolean.class),r.getLong("version"));}
 private Instant instant(ResultSet r,String c)throws SQLException{Timestamp t=r.getTimestamp(c);return t==null?null:t.toInstant();}
 record Row(UUID id,UUID tenantId,UUID dealId,UUID documentId,String objectVersion,String sha256,String status,Instant requestedAt,Instant processingStartedAt,Instant completedAt,Instant failedAt,String failureCode,Boolean retryRecommended,long version){}
}
