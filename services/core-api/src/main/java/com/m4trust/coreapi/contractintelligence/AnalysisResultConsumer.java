package com.m4trust.coreapi.contractintelligence;

import com.m4trust.coreapi.integration.messaging.AiResultsMessageRouter;
import com.m4trust.coreapi.integration.messaging.IntegrationViolation;
import org.springframework.stereotype.Service;

/** Backward-compatible entry for document result integration tests. */
@Service
final class AnalysisResultConsumer {

    private final AiResultsMessageRouter router;

    AnalysisResultConsumer(AiResultsMessageRouter router) {
        this.router = router;
    }

    void consume(String raw) {
        try {
            router.consume(raw);
        } catch (com.m4trust.coreapi.integration.messaging.IntegrationViolation exception) {
            throw new IntegrationViolation();
        }
    }

    static final class IntegrationViolation extends com.m4trust.coreapi.integration.messaging.IntegrationViolation {
    }
}
