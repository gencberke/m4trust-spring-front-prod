package com.m4trust.coreapi.integration.messaging;

import java.util.List;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Routes shared AI terminal events by job type after committed-schema validation. */
@Service
public final class AiResultsMessageRouter {

    private final ObjectMapper mapper;
    private final CommittedEventSchemaValidator validator;
    private final List<AiTerminalResultHandler> handlers;

    AiResultsMessageRouter(ObjectMapper mapper, CommittedEventSchemaValidator validator,
            List<AiTerminalResultHandler> handlers) {
        this.mapper = mapper;
        this.validator = validator;
        this.handlers = handlers;
    }

    public void consume(String raw) {
        final JsonNode event;
        try {
            event = mapper.readTree(raw);
        } catch (Exception exception) {
            throw new IntegrationViolation();
        }
        String eventType = requiredText(event, "eventType");
        String jobType = requiredText(event, "jobType");
        try {
            validateSchema(jobType, eventType, event);
            handlerFor(jobType).handle(event, eventType);
        } catch (CommittedEventSchemaValidator.ContractViolationException | IllegalArgumentException exception) {
            throw new IntegrationViolation();
        }
    }

    private AiTerminalResultHandler handlerFor(String jobType) {
        return handlers.stream()
                .filter(handler -> handler.jobType().equals(jobType))
                .findFirst()
                .orElseThrow(IntegrationViolation::new);
    }

    private void validateSchema(String jobType, String eventType, JsonNode event) {
        if ("DOCUMENT_EXTRACTION".equals(jobType)) {
            if ("ai.job.completed.v1".equals(eventType)) {
                validator.validateDocumentCompleted(event);
                return;
            }
            if ("ai.job.failed.v1".equals(eventType)) {
                validator.validateDocumentFailed(event);
                return;
            }
        }
        if ("VIDEO_ANALYSIS".equals(jobType)) {
            if ("ai.job.completed.v1".equals(eventType)) {
                validator.validateVideoCompleted(event);
                return;
            }
            if ("ai.job.failed.v1".equals(eventType)) {
                validator.validateVideoFailed(event);
                return;
            }
        }
        throw new IntegrationViolation();
    }

    private static String requiredText(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IntegrationViolation();
        }
        return value.asString();
    }
}
