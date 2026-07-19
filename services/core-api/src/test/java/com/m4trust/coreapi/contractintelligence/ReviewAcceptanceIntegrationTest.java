package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Acceptance boundary for the sole conversion from advisory extraction to immutable rules. */
@SpringBootTest(properties = {"app.messaging.topology.enabled=false", "app.messaging.relay.enabled=false", "spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("local") @Testcontainers @AutoConfigureMockMvc
@Import(ReviewAcceptanceIntegrationTest.Fakes.class)
class ReviewAcceptanceIntegrationTest {
    private static final String SHA = "a".repeat(64);
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.5-alpine");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired AtomicBoolean failAudit;
    UUID user, tenant, entity, deal, document, analysis, extraction;

    @BeforeEach void setUp() {
        jdbc.execute("TRUNCATE TABLE integration_outbox_event, integration_inbox_event, contract_intelligence_rule_set_version, contract_intelligence_extraction_result_version, contract_intelligence_analysis_job, http_idempotency_record, audit_record, deal_participant, document, deal, legal_entity_membership, legal_entity, tenant_user, tenant, identity_user CASCADE");
        user=UUID.randomUUID(); tenant=UUID.randomUUID(); entity=UUID.randomUUID(); deal=UUID.randomUUID(); document=UUID.randomUUID();
        seedActor(user, entity, "initiator"); seedDeal(); seedAnalysis(rules()); failAudit.set(false);
    }

    @Test void acceptsAllDecisionKindsPersistsExactRuleSetAndProjectsDeal() throws Exception {
        var result=accept(body(decisions("""
          {"decision":"KEPT","ruleReference":"keep"},
          {"decision":"MODIFIED","ruleReference":"modify","category":"PAYMENT","title":"Changed","description":"changed","structuredValue":{"type":"MONEY","amountMinor":1234,"currency":"TRY"}},
          {"decision":"EXCLUDED","ruleReference":"exclude"},
          {"decision":"ADDED","category":"PENALTY","title":"Manual","description":"manual","structuredValue":{"type":"PERCENTAGE","basisPoints":250}}
          """)), UUID.randomUUID())
          .andExpect(status().isCreated()).andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.rules.length()").value(3))
          .andExpect(jsonPath("$.rules[0].legalBasisProvenance").value("EXTRACTED"))
          .andExpect(jsonPath("$.rules[1].legalBasisProvenance").value("REVIEWER_MODIFIED"))
          .andExpect(jsonPath("$.rules[2].legalBasis").value(org.hamcrest.Matchers.nullValue()))
          .andExpect(jsonPath("$.excludedRuleReferences[0]").value("exclude")).andReturn();
        String version=jdbc.queryForObject("select id::text from contract_intelligence_rule_set_version where deal_id=?", String.class, deal);
        assertEquals("ACCEPTED", text("select status from contract_intelligence_analysis_job where id=?",analysis));
        assertEquals(1, count("contract_intelligence_rule_set_version")); assertEquals(1,count("audit_record")); assertEquals(1,count("http_idempotency_record"));
        assertEquals(1L, jdbc.queryForObject("select version from deal where id=?",Long.class,deal));
        mvc.perform(get("/api/v1/deals/"+deal).with(user(user.toString())).header(h(),entity)).andExpect(status().isOk()).andExpect(jsonPath("$.lifecycle").value("RATIFICATION")).andExpect(jsonPath("$.currentRuleSet.version").value(1)).andExpect(jsonPath("$.availableActions.canReviewExtraction").value(false));
        UUID participant=UUID.randomUUID(), participantEntity=UUID.randomUUID(); seedActor(participant,participantEntity,"participant"); participant(participantEntity);
        mvc.perform(get("/api/v1/deals/"+deal+"/rule-set-versions").with(user(participant.toString())).header(h(),participantEntity)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(version));
        mvc.perform(get("/api/v1/deals/"+deal+"/rule-set-versions/"+version).with(user(participant.toString())).header(h(),participantEntity)).andExpect(status().isOk()).andExpect(jsonPath("$.rules[1].structuredValue.amountMinor").value(1234));
        mvc.perform(get("/api/v1/deals/"+UUID.randomUUID()+"/rule-set-versions/"+version).with(user(participant.toString())).header(h(),participantEntity)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RULE_SET_VERSION_NOT_FOUND"));
    }

    @Test void acceptsAnEmptyExtractionWithNoDecisions() throws Exception {
        seedAnalysis("{\"parties\":[],\"rules\":[],\"deliveryRequirements\":[],\"summary\":{\"requiresManualReview\":false,\"reviewReasons\":[]}}");
        accept(body("[]"),UUID.randomUUID()).andExpect(status().isCreated()).andExpect(jsonPath("$.ruleCount").value(0)).andExpect(jsonPath("$.rules").isEmpty());
    }

    @Test void idempotencyReplaysSemanticJsonAndRejectsChangedRequest() throws Exception {
        UUID key=UUID.randomUUID(); String request=body(decisions("{\"decision\":\"KEPT\",\"ruleReference\":\"keep\"},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}"));
        accept(request,key).andExpect(status().isCreated());
        String reordered = "{\"decisions\":[{\"ruleReference\":\"keep\",\"decision\":\"KEPT\"},{\"ruleReference\":\"modify\",\"decision\":\"KEPT\"},{\"ruleReference\":\"exclude\",\"decision\":\"KEPT\"}],\"expectedVersion\":0,\"analysisId\":\"" + analysis + "\"}";
        accept(reordered,key).andExpect(status().isCreated());
        accept(body(decisions("{\"decision\":\"EXCLUDED\",\"ruleReference\":\"keep\"},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}")),key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(1,count("contract_intelligence_rule_set_version")); assertEquals(1,count("audit_record"));
    }

    @Test void mapsMalformedValidationAndStateConflictsWithoutSideEffects() throws Exception {
        accept("{",UUID.randomUUID()).andExpect(status().isBadRequest());
        String negative=body(decisions("{\"decision\":\"MODIFIED\",\"ruleReference\":\"keep\",\"category\":\"PAYMENT\",\"title\":\"x\",\"description\":\"x\",\"structuredValue\":{\"type\":\"MONEY\",\"amountMinor\":-1,\"currency\":\"TRY\"}},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}"));
        accept(negative,UUID.randomUUID()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].field").value("decisions[0].structuredValue.amountMinor"));
        accept(body(decisions("{\"decision\":\"KEPT\",\"ruleReference\":\"keep\"}")),UUID.randomUUID()).andExpect(status().isBadRequest());
        assertEquals(0,count("contract_intelligence_rule_set_version"));
    }

    @Test void participantReadsButCannotAcceptAndOutsiderGetsContractSpecificNotFound() throws Exception {
        UUID p=UUID.randomUUID(), pe=UUID.randomUUID(), outsider=UUID.randomUUID(), oe=UUID.randomUUID(); seedActor(p,pe,"p"); participant(pe); seedActor(outsider,oe,"o");
        mvc.perform(get("/api/v1/deals/"+deal+"/extraction-review").with(user(p.toString())).header(h(),pe)).andExpect(status().isOk());
        acceptAs(p,pe,body(decisions("{\"decision\":\"KEPT\",\"ruleReference\":\"keep\"},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}")),UUID.randomUUID()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEAL_REVIEW_ACCEPTANCE_FORBIDDEN"));
        mvc.perform(get("/api/v1/deals/"+deal+"/rule-set-versions/"+UUID.randomUUID()).with(user(outsider.toString())).header(h(),oe)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RULE_SET_VERSION_NOT_FOUND"));
        mvc.perform(get("/api/v1/deals/"+deal+"/rule-set-versions").with(user(outsider.toString())).header(h(),oe)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
    }

    @Test void auditFailureRollsBackEveryAcceptanceWrite() throws Exception {
        long initialDealVersion = jdbc.queryForObject("select version from deal where id=?", Long.class, deal);
        long initialAnalysisVersion = jdbc.queryForObject("select version from contract_intelligence_analysis_job where id=?", Long.class, analysis);
        failAudit.set(true); accept(completeBody(),UUID.randomUUID()).andExpect(status().is5xxServerError());
        assertEquals(0,count("contract_intelligence_rule_set_version")); assertEquals("REVIEW_REQUIRED",text("select status from contract_intelligence_analysis_job where id=?",analysis)); assertEquals(initialDealVersion, jdbc.queryForObject("select version from deal where id=?", Long.class, deal)); assertEquals(initialAnalysisVersion, jdbc.queryForObject("select version from contract_intelligence_analysis_job where id=?", Long.class, analysis)); assertEquals(0,count("audit_record")); assertEquals(0,count("http_idempotency_record")); assertNull(jdbc.queryForObject("select current_rule_set_version_id from deal where id=?",UUID.class,deal));
    }

    @Test void databaseEnforcesInsertOnlyAndSameDealPointers() throws Exception {
        var accepted=accept(body(decisions("{\"decision\":\"KEPT\",\"ruleReference\":\"keep\"},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}")),UUID.randomUUID()).andReturn();
        UUID id=jdbc.queryForObject("select id from contract_intelligence_rule_set_version where deal_id=?",UUID.class,deal);
        assertThrows(Exception.class,()->jdbc.update("update contract_intelligence_rule_set_version set version=2 where id=?",id)); assertThrows(Exception.class,()->jdbc.update("delete from contract_intelligence_rule_set_version where id=?",id));
        UUID otherDeal = UUID.randomUUID();
        jdbc.update("insert into deal(id,tenant_id,reference,title,deal_status,initiator_legal_entity_id,created_by) values(?,?, 'DL-0000000002','other','DRAFT',?,?)", otherDeal, tenant, entity, user);
        assertThrows(Exception.class, () -> jdbc.update("update deal set current_rule_set_version_id=? where id=?", id, otherDeal));
    }

    @Test void rejectsStaleWrongNonCurrentSupersededAndTerminalAcceptancesWithoutWrites() throws Exception {
        accept(bodyWithVersion(1, analysis), UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_STALE_VERSION"));
        accept(body(UUID.randomUUID()), UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        UUID nonCurrent = seedNonCurrentAnalysis();
        accept(body(nonCurrent), UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        jdbc.update("update contract_intelligence_analysis_job set status='SUPERSEDED' where id=?", analysis);
        accept(body(analysis), UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        jdbc.update("update deal set deal_status='CANCELLED' where id=?", deal);
        accept(body(analysis), UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_STATE_CONFLICT"));
        assertEquals(0, count("contract_intelligence_rule_set_version"));
        assertEquals(0, count("audit_record"));
        assertEquals(0, count("http_idempotency_record"));
    }

    @Test void concurrentAcceptsCreateExactlyOneVersionWithoutDeadlock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> { start.await(); return accept(completeBody(), UUID.randomUUID()).andReturn().getResponse().getStatus(); });
            Future<Integer> second = executor.submit(() -> { start.await(); return accept(completeBody(), UUID.randomUUID()).andReturn().getResponse().getStatus(); });
            start.countDown();
            List<Integer> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(code -> code == 201).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
            assertEquals(1, count("contract_intelligence_rule_set_version"));
            assertEquals(1, count("audit_record"));
            assertNotNull(jdbc.queryForObject("select current_rule_set_version_id from deal where id=?", UUID.class, deal));
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions accept(String b, UUID key) throws Exception { return acceptAs(user,entity,b,key); }
    private org.springframework.test.web.servlet.ResultActions acceptAs(UUID u,UUID e,String b,UUID key) throws Exception { return mvc.perform(post("/api/v1/deals/"+deal+"/extraction-review/accept").with(user(u.toString())).with(csrf()).header(h(),e).header("Idempotency-Key",key).contentType("application/json").content(b)); }
    private String body(String decisions) { return "{\"analysisId\":\""+analysis+"\",\"expectedVersion\":0,\"decisions\":"+decisions+"}"; }
    private String body(UUID analysisId) { return bodyWithVersion(0, analysisId); }
    private String bodyWithVersion(long expectedVersion, UUID analysisId) { return "{\"analysisId\":\""+analysisId+"\",\"expectedVersion\":"+expectedVersion+",\"decisions\":"+completeDecisions()+"}"; }
    private String completeBody() { return bodyWithVersion(0, analysis); }
    private String completeDecisions() { return decisions("{\"decision\":\"KEPT\",\"ruleReference\":\"keep\"},{\"decision\":\"KEPT\",\"ruleReference\":\"modify\"},{\"decision\":\"KEPT\",\"ruleReference\":\"exclude\"}"); }
    private String decisions(String values) { return "["+values+"]"; }
    private String h(){return "X-M4Trust-Legal-Entity-Id";} private int count(String t){return jdbc.queryForObject("select count(*) from "+t,Integer.class);} private String text(String q,Object a){return jdbc.queryForObject(q,String.class,a);}
    private void seedActor(UUID u,UUID e,String label){ jdbc.update("insert into identity_user(id,email,password_hash,display_name,enabled) values(?,?, 'x',?,true)",u,u+"@test",label); jdbc.update("insert into tenant(id) values(?) on conflict do nothing",tenant); jdbc.update("insert into tenant_user(user_id,tenant_id) values(?,?)",u,tenant); jdbc.update("insert into legal_entity(id,tenant_id,legal_name,registration_number) values(?,?,?,?)",e,tenant,label,label+e); jdbc.update("insert into legal_entity_membership(id,tenant_id,legal_entity_id,user_id,role) values(?,?,?,?, 'ADMIN')",UUID.randomUUID(),tenant,e,u); }
    private void seedDeal(){jdbc.update("insert into deal(id,tenant_id,reference,title,deal_status,initiator_legal_entity_id,created_by) values(?,?, 'DL-0000000001','d','DRAFT',?,?)",deal,tenant,entity,user); participant(entity); jdbc.update("insert into document(id,deal_id,file_name,media_type,document_status,object_key,declared_size_bytes,declared_sha256,upload_expires_at,verified_size_bytes,verified_sha256,object_version,created_at,available_at,updated_at) values(?,?, 'd.pdf','application/pdf','AVAILABLE',?,1,?,current_timestamp + interval '1 hour',1,?,'v',current_timestamp,current_timestamp,current_timestamp)",document,deal,"d/"+document,SHA,SHA); jdbc.update("update deal set current_document_id=?,current_document_status='AVAILABLE' where id=?",document,deal);}
    private void participant(UUID e){jdbc.update("insert into deal_participant(deal_id,tenant_id,legal_entity_id,legal_entity_tenant_id) values(?,?,?,?)",deal,tenant,e,tenant);}
    private void seedAnalysis(String canonical){ analysis=UUID.randomUUID(); extraction=UUID.randomUUID(); jdbc.update("insert into contract_intelligence_analysis_job(id,tenant_id,deal_id,document_id,object_version,input_sha256,status,requested_at,processing_started_at,completed_at,version) values(?,?,?,?,?,'"+SHA+"','REVIEW_REQUIRED',current_timestamp,current_timestamp,current_timestamp,0)",analysis,tenant,deal,document,"v"); jdbc.update("insert into contract_intelligence_extraction_result_version(id,analysis_job_id,schema_version,canonical_result,created_at) values(?,?,'1.0.0',cast(? as jsonb),current_timestamp)",extraction,analysis,canonical);}
    private UUID seedNonCurrentAnalysis() {
        UUID nonCurrentDocument = UUID.randomUUID();
        UUID nonCurrentAnalysis = UUID.randomUUID();
        UUID nonCurrentExtraction = UUID.randomUUID();
        jdbc.update("insert into document(id,deal_id,file_name,media_type,document_status,object_key,declared_size_bytes,declared_sha256,upload_expires_at,verified_size_bytes,verified_sha256,object_version,created_at,available_at,updated_at) values(?,?, 'other.pdf','application/pdf','AVAILABLE',?,1,?,current_timestamp + interval '1 hour',1,?,'v2',current_timestamp,current_timestamp,current_timestamp)", nonCurrentDocument, deal, "d/"+nonCurrentDocument, SHA, SHA);
        jdbc.update("insert into contract_intelligence_analysis_job(id,tenant_id,deal_id,document_id,object_version,input_sha256,status,requested_at,processing_started_at,completed_at,version) values(?,?,?,?,?,'"+SHA+"','REVIEW_REQUIRED',current_timestamp,current_timestamp,current_timestamp,0)", nonCurrentAnalysis, tenant, deal, nonCurrentDocument, "v2");
        jdbc.update("insert into contract_intelligence_extraction_result_version(id,analysis_job_id,schema_version,canonical_result,created_at) values(?,?,'1.0.0',cast(? as jsonb),current_timestamp)", nonCurrentExtraction, nonCurrentAnalysis, rules());
        return nonCurrentAnalysis;
    }
    private String rules(){return "{\"parties\":[],\"rules\":[{\"ruleReference\":\"keep\",\"category\":\"OTHER\",\"title\":\"Keep\",\"description\":\"keep\",\"structuredValue\":{\"type\":\"TEXT\",\"value\":\"ok\"},\"confidence\":0.9,\"sourceReferences\":[],\"legalBasis\":{\"source\":\"tbk-6098\",\"articleNo\":\"1\"}},{\"ruleReference\":\"modify\",\"category\":\"PAYMENT\",\"title\":\"Modify\",\"description\":\"modify\",\"structuredValue\":{\"type\":\"MONEY\",\"amountMinor\":100,\"currency\":\"TRY\"},\"confidence\":0.8,\"sourceReferences\":[],\"legalBasis\":{\"source\":\"tbk-6098\",\"articleNo\":\"2\"}},{\"ruleReference\":\"exclude\",\"category\":\"OTHER\",\"title\":\"Exclude\",\"description\":\"exclude\",\"structuredValue\":{\"type\":\"TEXT\",\"value\":\"no\"},\"confidence\":0.7,\"sourceReferences\":[],\"legalBasis\":null}],\"deliveryRequirements\":[],\"summary\":{\"requiresManualReview\":false,\"reviewReasons\":[]}}";}
    @TestConfiguration(proxyBeanMethods=false) static class Fakes { @Bean AtomicBoolean failAudit(){return new AtomicBoolean();} @Bean @Primary AuditAppendPort auditAppendPort(JdbcTemplate j,AtomicBoolean f){return r->{if(f.get())throw new IllegalStateException("forced");j.update("insert into audit_record(id,tenant_id,actor_user_id,legal_entity_id,subject_type,subject_id,action,correlation_id,causation_id,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",r.id(),r.tenantId(),r.actorUserId(),r.legalEntityId(),r.subjectType(),r.subjectId(),r.action(),r.correlationId(),r.causationId(),Timestamp.from(r.occurredAt()));};}}
}
