package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.messaging.topology.enabled=false", "app.messaging.relay.enabled=false",
        "spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("local")
@Testcontainers
@AutoConfigureMockMvc
@Import(AnalysisResultConsumerIntegrationTest.Fakes.class)
class AnalysisResultConsumerIntegrationTest {
    private static final String SHA = "a".repeat(64);
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");
    @Autowired AnalysisResultConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AtomicBoolean failAudit;
    @Autowired MockMvc mockMvc;
    UUID tenant, deal, document, job, user, entity;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE TABLE integration_inbox_event, contract_intelligence_rule_set_version, contract_intelligence_extraction_result_version, contract_intelligence_analysis_job, http_idempotency_record, audit_record, deal_invitation, deal_participant, document, ratification_package_approval, ratification_package, ratification_package_snapshot, deal, legal_entity_membership, legal_entity, tenant_user, tenant, identity_user");
        tenant=UUID.randomUUID(); deal=UUID.randomUUID(); document=UUID.randomUUID(); job=UUID.randomUUID();
        user=UUID.randomUUID(); entity=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user(id,email,password_hash,display_name,enabled) VALUES(?,?,'x','x',true)",user,user+"@t");
        jdbc.update("INSERT INTO tenant(id) VALUES(?)",tenant); jdbc.update("INSERT INTO tenant_user(user_id,tenant_id) VALUES(?,?)",user,tenant);
        jdbc.update("INSERT INTO legal_entity(id,tenant_id,legal_name,registration_number) VALUES(?,?, 'x','x')",entity,tenant);
        jdbc.update("INSERT INTO legal_entity_membership(id,tenant_id,legal_entity_id,user_id,role) VALUES(?,?,?,?, 'ADMIN')",UUID.randomUUID(),tenant,entity,user);
        jdbc.update("INSERT INTO deal(id,tenant_id,reference,title,deal_status,initiator_legal_entity_id,created_by) VALUES(?,?, 'DL-0000000001','x','DRAFT',?,?)",deal,tenant,entity,user);
        jdbc.update("INSERT INTO deal_participant(deal_id,tenant_id,legal_entity_id,legal_entity_tenant_id) VALUES(?,?,?,?)",deal,tenant,entity,tenant);
        jdbc.update("INSERT INTO document(id,deal_id,file_name,media_type,document_status,object_key,declared_size_bytes,declared_sha256, upload_expires_at,verified_size_bytes,verified_sha256,object_version,created_at,available_at,updated_at) VALUES(?,?,'x.pdf','application/pdf','AVAILABLE','x',1,?,CURRENT_TIMESTAMP + INTERVAL '1 hour',1,?,'v',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",document,deal,SHA,SHA);
        jdbc.update("UPDATE deal SET current_document_id=?, current_document_status='AVAILABLE' WHERE id=?",document,deal);
        insertJob(AnalysisJobStatus.QUEUED); failAudit.set(false);
    }

    @Test void completedIsImmutableIdempotentAndPublicProjectionIsSafe() throws Exception {
        UUID eventId=UUID.randomUUID(); consumer.consume(json(completed(eventId, job, SHA)));
        consumer.consume(json(completed(eventId, job, SHA)));
        assertEquals("REVIEW_REQUIRED", jobStatus()); assertEquals(1,count("integration_inbox_event")); assertEquals(1,count("contract_intelligence_extraction_result_version"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE action='AI_DOCUMENT_EXTRACTION_COMPLETED'",Integer.class));
        String stored=jdbc.queryForObject("SELECT canonical_result::text FROM contract_intelligence_extraction_result_version",String.class);
        assertEquals(true, mapper.readTree(stored).has("document"));
        assertEquals("tbk-6098", mapper.readTree(stored).path("rules").get(0).path("legalBasis").path("source").asString());
    }

    @Test void completedPublicHttpProjectionMatchesTheCommittedShape() throws Exception {
        consumer.consume(json(completed(UUID.randomUUID(), job, SHA)));

        mockMvc.perform(get("/api/v1/deals/" + deal + "/document-analysis")
                        .with(user(user.toString()))
                        .header("X-M4Trust-Legal-Entity-Id", entity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.result.summary.requiresManualReview").value(false))
                .andExpect(jsonPath("$.result.rules[0].legalBasis.source").value("tbk-6098"))
                .andExpect(jsonPath("$.result.rules[0].legalBasis.articleNo").value("1"))
                .andExpect(jsonPath("$.result.document").doesNotExist());
    }

    @Test void contractInvalidEventsRollBackInboxAndLeaveQueuedBusinessState() throws Exception {
        Map<String,Object> wrongVersion = completed(UUID.randomUUID(), job, SHA);
        wrongVersion.put("schemaVersion", "1.0.1");
        assertIntegrationViolationIsAtomic(wrongVersion);

        Map<String,Object> categoryCodeMismatch = failed(UUID.randomUUID(), job);
        error(categoryCodeMismatch).put("category", "INVALID_INPUT");
        assertIntegrationViolationIsAtomic(categoryCodeMismatch);

        Map<String,Object> retryMismatch = failed(UUID.randomUUID(), job);
        error(retryMismatch).put("retryRecommended", false);
        assertIntegrationViolationIsAtomic(retryMismatch);

        Map<String,Object> attemptMismatch = failed(UUID.randomUUID(), job);
        attempt(attemptMismatch).put("attemptNumber", 2);
        assertIntegrationViolationIsAtomic(attemptMismatch);
    }

    @Test void identityAndContentHashMismatchesRollBackInboxAndLeaveQueuedBusinessState() throws Exception {
        for (String identity : List.of("tenantId", "transactionId", "subjectId", "jobId")) {
            Map<String,Object> mismatch = completed(UUID.randomUUID(), job, SHA);
            mismatch.put(identity, UUID.randomUUID().toString());
            assertIntegrationViolationIsAtomic(mismatch);
        }
        assertIntegrationViolationIsAtomic(completed(UUID.randomUUID(), job, "b".repeat(64)));
    }

    @Test void terminalSupersededAndInvalidEventsNeverMutateIncorrectly() throws Exception {
        consumer.consume(json(completed(UUID.randomUUID(),job,SHA)));
        consumer.consume(json(failed(UUID.randomUUID(),job)));
        assertEquals("REVIEW_REQUIRED",jobStatus()); assertEquals(1,count("contract_intelligence_extraction_result_version"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_record WHERE action='AI_ANALYSIS_TERMINAL_EVENT_IGNORED'",Integer.class));
        UUID second=UUID.randomUUID(); insertJob(second,AnalysisJobStatus.SUPERSEDED);
        consumer.consume(json(completed(UUID.randomUUID(),second,SHA)));
        assertEquals("SUPERSEDED", jdbc.queryForObject("SELECT status FROM contract_intelligence_analysis_job WHERE id=?",String.class,second));
        assertEquals(1,count("contract_intelligence_extraction_result_version"));
        UUID third=UUID.randomUUID(); insertJob(third,AnalysisJobStatus.QUEUED);
        assertThrows(AnalysisResultConsumer.IntegrationViolation.class,()->consumer.consume(json(completed(UUID.randomUUID(),third,"b".repeat(64)))));
        assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM contract_intelligence_analysis_job WHERE id=?",String.class,third));
        assertEquals(3,count("integration_inbox_event"));
    }

    @Test void lateCompletedEventForAcceptedAnalysisIsInboxedAndIgnoredWithoutCreatingRules() throws Exception {
        consumer.consume(json(completed(UUID.randomUUID(), job, SHA)));
        jdbc.update("UPDATE contract_intelligence_analysis_job SET status = 'ACCEPTED' WHERE id = ?", job);
        String resultBefore = jdbc.queryForObject("""
                SELECT canonical_result::text FROM contract_intelligence_extraction_result_version
                WHERE analysis_job_id = ?
                """, String.class, job);

        consumer.consume(json(completed(UUID.randomUUID(), job, SHA)));

        assertEquals("ACCEPTED", jobStatus());
        assertEquals(resultBefore, jdbc.queryForObject("""
                SELECT canonical_result::text FROM contract_intelligence_extraction_result_version
                WHERE analysis_job_id = ?
                """, String.class, job));
        assertEquals(1, count("contract_intelligence_extraction_result_version"));
        assertEquals(0, count("contract_intelligence_rule_set_version"));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM deal WHERE id = ? AND current_rule_set_version_id IS NOT NULL
                """, Integer.class, deal));
        assertEquals(2, count("integration_inbox_event"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_record WHERE action = 'AI_ANALYSIS_TERMINAL_EVENT_IGNORED'
                """, Integer.class));
    }

    @Test void failedAndAuditFailureAreAtomic() throws Exception {
        consumer.consume(json(failed(UUID.randomUUID(),job)));
        assertEquals("FAILED",jobStatus()); assertEquals("MODEL_PROVIDER_TIMEOUT",jdbc.queryForObject("SELECT failure_code FROM contract_intelligence_analysis_job WHERE id=?",String.class,job));
        UUID next=UUID.randomUUID(); insertJob(next,AnalysisJobStatus.QUEUED); failAudit.set(true);
        assertThrows(IllegalStateException.class,()->consumer.consume(json(completed(UUID.randomUUID(),next,SHA))));
        assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM contract_intelligence_analysis_job WHERE id=?",String.class,next));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM contract_intelligence_extraction_result_version WHERE analysis_job_id=?",Integer.class,next));
    }

    private void assertIntegrationViolationIsAtomic(Map<String,Object> event) throws Exception {
        int inboxBefore = count("integration_inbox_event");
        int auditBefore = count("audit_record");
        assertThrows(AnalysisResultConsumer.IntegrationViolation.class,
                () -> consumer.consume(json(event)));
        assertEquals("QUEUED", jobStatus());
        assertEquals(inboxBefore, count("integration_inbox_event"));
        assertEquals(auditBefore, count("audit_record"));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM contract_intelligence_extraction_result_version
                WHERE analysis_job_id = ?
                """, Integer.class, job));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM contract_intelligence_analysis_job
                WHERE id = ? AND (failure_code IS NOT NULL OR retry_recommended IS NOT NULL
                    OR processing_started_at IS NOT NULL OR completed_at IS NOT NULL
                    OR failed_at IS NOT NULL)
                """, Integer.class, job));
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> error(Map<String,Object> event) {
        return (Map<String,Object>) ((Map<String,Object>) event.get("payload")).get("error");
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> attempt(Map<String,Object> event) {
        return (Map<String,Object>) ((Map<String,Object>) event.get("payload")).get("attempt");
    }

    private void insertJob(AnalysisJobStatus s) { insertJob(job,s); }
    private void insertJob(UUID id,AnalysisJobStatus s) { jdbc.update("INSERT INTO contract_intelligence_analysis_job(id,tenant_id,deal_id,document_id,object_version,input_sha256,status,requested_at,version, processing_started_at,completed_at,failed_at,failure_code,retry_recommended) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,0,?,?,?,?,?)",id,tenant,deal,document,"v",SHA,s.name(),
            s==AnalysisJobStatus.REVIEW_REQUIRED?Timestamp.from(java.time.Instant.now()):null,s==AnalysisJobStatus.REVIEW_REQUIRED?Timestamp.from(java.time.Instant.now()):null,
            s==AnalysisJobStatus.FAILED?Timestamp.from(java.time.Instant.now()):null,s==AnalysisJobStatus.FAILED?"MODEL_PROVIDER_TIMEOUT":null,s==AnalysisJobStatus.FAILED?true:null); }
    private String jobStatus(){return jdbc.queryForObject("SELECT status FROM contract_intelligence_analysis_job WHERE id=?",String.class,job);}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private String json(Map<String,Object> value)throws Exception{return mapper.writeValueAsString(value);}
    private Map<String,Object> completed(UUID event,UUID id,String hash){Map<String,Object> r=new LinkedHashMap<>();r.put("document",Map.of("detectedMediaType","application/pdf","detectedLanguage","tr","pageCount",1,"textExtractionMethod","DIGITAL_PDF","contentSha256",hash));r.put("parties",List.of());r.put("rules",List.of(Map.of("ruleReference","r","category","OTHER","title","t","description","d","structuredValue",Map.of("type","TEXT","value","v"),"confidence",0.5,"sourceReferences",List.of(),"legalBasis",Map.of("source","tbk-6098","articleNo","1"))));r.put("deliveryRequirements",List.of());r.put("summary",Map.of("requiresManualReview",false,"reviewReasons",List.of()));return envelope(event,id,"ai.job.completed.v1",Map.of("result",r,"technicalMetadata",Map.of("pipelineVersion","1","durationMs",0),"warnings",List.of()));}
    private Map<String,Object> failed(UUID event,UUID id){
        Map<String,Object> error=new LinkedHashMap<>(); error.put("category","RETRYABLE_TECHNICAL"); error.put("code","MODEL_PROVIDER_TIMEOUT"); error.put("message","safe"); error.put("retryRecommended",true); error.put("details",null);
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("error",error); payload.put("attempt",new LinkedHashMap<>(Map.of("attemptNumber",1,"maxAttempts",1))); payload.put("technicalMetadata",Map.of("pipelineVersion","1","durationMs",0));
        return envelope(event,id,"ai.job.failed.v1",payload);
    }
    private Map<String,Object> envelope(UUID event,UUID id,String type,Object payload){Map<String,Object> e=new LinkedHashMap<>();e.put("eventId",event.toString());e.put("eventType",type);e.put("schemaVersion","1.0.0");e.put("occurredAt","2026-07-19T00:00:00Z");e.put("correlationId",UUID.randomUUID().toString());e.put("causationId",null);e.put("jobId",id.toString());e.put("jobType","DOCUMENT_EXTRACTION");e.put("tenantId",tenant.toString());e.put("transactionId",deal.toString());e.put("subjectId",document.toString());e.put("idempotencyKey","k");e.put("producer",Map.of("service","worker","version","1.0.0"));e.put("payload",payload);return e;}

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean AtomicBoolean failAudit() { return new AtomicBoolean(); }
        @Bean @Primary com.m4trust.coreapi.audit.AuditAppendPort auditAppendPort(JdbcTemplate jdbc, AtomicBoolean failAudit) {
            return record -> { if (failAudit.get()) throw new IllegalStateException("forced audit failure"); jdbc.update("INSERT INTO audit_record(id,tenant_id,actor_user_id,legal_entity_id,subject_type,subject_id,action,correlation_id,causation_id,occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?)",record.id(),record.tenantId(),record.actorUserId(),record.legalEntityId(),record.subjectType(),record.subjectId(),record.action(),record.correlationId(),record.causationId(),Timestamp.from(record.occurredAt())); };
        }
    }
}
