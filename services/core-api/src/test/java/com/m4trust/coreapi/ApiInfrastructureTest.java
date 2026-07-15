package com.m4trust.coreapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.m4trust.coreapi.api.ApiExceptionHandler;
import com.m4trust.coreapi.api.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiInfrastructureTest {

    private static final String PROBE_PATH = "/test-only/validation-probe";
    private static final String UNEXPECTED_PATH = "/test-only/unexpected-error";

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

    @Test
    void unexpectedExceptionReturnsSafe500AndLogsCorrelationContext() throws Exception {
        String correlationId = "2dfe0e12-e5ad-42c7-9e39-35617d86612e";
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            mockMvc.perform(get(UNEXPECTED_PATH)
                            .header(CorrelationIdFilter.HEADER, correlationId))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type")
                            .value("https://problems.m4trust.internal/internal-error"))
                    .andExpect(jsonPath("$.title").value("Unexpected error"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.detail")
                            .value("The request could not be completed."))
                    .andExpect(jsonPath("$.instance").value(UNEXPECTED_PATH))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.correlationId").value(correlationId))
                    .andExpect(result -> assertFalse(result.getResponse()
                            .getContentAsString().contains("sensitive internal detail")));

            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains(correlationId)
                            && event.getFormattedMessage().contains(UNEXPECTED_PATH)
                            && event.getThrowableProxy() != null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void wrongMethodRemainsFramework405InsteadOfInternalError() throws Exception {
        mockMvc.perform(get(PROBE_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(result -> assertFalse(result.getResponse()
                        .getContentAsString().contains("INTERNAL_ERROR")));
    }

    @RestController
    static class ValidationProbeController {

        @PostMapping(PROBE_PATH)
        void validate(@Valid @RequestBody ValidationProbeRequest request) {
            // Test-only probe: production has no Slice 0 public application endpoint.
        }

        @GetMapping(UNEXPECTED_PATH)
        void unexpected() {
            throw new IllegalStateException("sensitive internal detail");
        }
    }

    record ValidationProbeRequest(
            @NotBlank(message = "message must not be blank") String message) {
    }
}
