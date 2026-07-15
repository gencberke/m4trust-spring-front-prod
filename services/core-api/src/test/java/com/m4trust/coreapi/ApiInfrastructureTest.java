package com.m4trust.coreapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.m4trust.coreapi.api.ApiExceptionHandler;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiInfrastructureTest {

    private static final String PROBE_PATH = "/test-only/validation-probe";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void blankMessageReturnsProblemDetailsWith422AndGeneratedCorrelationId() throws Exception {
        mockMvc.perform(post(PROBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://problems.m4trust.internal/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("One or more fields are invalid."))
                .andExpect(jsonPath("$.instance").value(PROBE_PATH))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("message"))
                .andExpect(result -> {
                    String correlationId = result.getResponse()
                            .getHeader(CorrelationIdFilter.HEADER);
                    assertNotNull(correlationId);
                    assertDoesNotThrow(() -> UUID.fromString(correlationId));
                    assertTrue(result.getResponse().getContentAsString().contains(correlationId));
                });
    }

    @Test
    void suppliedCorrelationIdIsPreservedInResponseAndProblemContext() throws Exception {
        String correlationId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        mockMvc.perform(post(PROBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}")
                        .header(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    void malformedJsonReturnsProblemDetailsWith400() throws Exception {
        mockMvc.perform(post(PROBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://problems.m4trust.internal/malformed-request"))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The request body could not be parsed."))
                .andExpect(jsonPath("$.instance").value(PROBE_PATH))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @RestController
    static class ValidationProbeController {

        @PostMapping(PROBE_PATH)
        void validate(@Valid @RequestBody ValidationProbeRequest request) {
            // Test-only probe: production has no Slice 0 public application endpoint.
        }
    }

    record ValidationProbeRequest(
            @NotBlank(message = "message must not be blank") String message) {
    }
}
