package com.m4trust.coreapi.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

class FulfillmentExceptionHandlerTest {

    private final FulfillmentExceptionHandler handler = new FulfillmentExceptionHandler();

    @Test
    void dealNotFoundUsesGranularCode() {
        ResponseEntity<ProblemDetail> response = handler.handleDealNotFound(
                new MockHttpServletRequest("GET", "/api/v1/deals/x/fulfillment"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("DEAL_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("https://problems.m4trust.internal/deal-not-found",
                response.getBody().getType().toString());
        assertEquals("Deal not found", response.getBody().getTitle());
    }

    @Test
    void fulfillmentNotFoundUsesGranularCode() {
        ResponseEntity<ProblemDetail> response = handler.handleFulfillmentNotFound(
                new MockHttpServletRequest("GET", "/api/v1/deals/x/fulfillment"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("FULFILLMENT_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("https://problems.m4trust.internal/fulfillment-not-found",
                response.getBody().getType().toString());
        assertEquals("Fulfillment not found", response.getBody().getTitle());
    }

    @Test
    void evidenceNotFoundUsesGranularCode() {
        ResponseEntity<ProblemDetail> response = handler.handleEvidenceNotFound(
                new MockHttpServletRequest("GET", "/api/v1/deals/x/fulfillment/evidence/y"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("EVIDENCE_NOT_FOUND", response.getBody().getProperties().get("code"));
        assertEquals("https://problems.m4trust.internal/evidence-not-found",
                response.getBody().getType().toString());
        assertEquals("Evidence not found", response.getBody().getTitle());
    }

    @Test
    void authorizationOrderPreservesForbiddenBeforeNotFoundSemanticsInHandlerMapping() {
        ResponseEntity<ProblemDetail> forbidden = handler.handleStartForbidden(
                new MockHttpServletRequest("POST", "/api/v1/deals/x/fulfillment"));
        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals("FULFILLMENT_START_FORBIDDEN",
                forbidden.getBody().getProperties().get("code"));

        ResponseEntity<ProblemDetail> dealMissing = handler.handleDealNotFound(
                new MockHttpServletRequest("GET", "/api/v1/deals/x/fulfillment"));
        assertEquals(404, dealMissing.getStatusCode().value());
        assertEquals("DEAL_NOT_FOUND", dealMissing.getBody().getProperties().get("code"));
    }

    @Test
    void serializesGranularCodesThroughMvc() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(handler)
                .build();

        mvc.perform(get("/deal-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEAL_NOT_FOUND"));
        mvc.perform(get("/fulfillment-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
        mvc.perform(get("/evidence-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    @Controller
    static class Probe {
        @GetMapping("/deal-missing")
        void dealMissing() {
            throw new FulfillmentExceptions.DealNotFound();
        }

        @GetMapping("/fulfillment-missing")
        void fulfillmentMissing() {
            throw new FulfillmentExceptions.FulfillmentNotFound();
        }

        @GetMapping("/evidence-missing")
        void evidenceMissing() {
            throw new FulfillmentExceptions.EvidenceNotFound();
        }
    }
}
