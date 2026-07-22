package com.m4trust.coreapi.integration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rabbit boundary: invalid integration traffic is safely dead-lettered, never business-failed. */
@Component
@ConditionalOnProperty(prefix = "app.messaging.topology", name = "enabled", havingValue = "true")
final class AiResultsRabbitListener {

    private static final Logger log = LoggerFactory.getLogger(AiResultsRabbitListener.class);
    private final AiResultsMessageRouter router;

    AiResultsRabbitListener(AiResultsMessageRouter router) {
        this.router = router;
    }

    @RabbitListener(queues = "m4trust.core.ai-results.v1", containerFactory = "aiResultsListenerFactory")
    void onMessage(String raw) {
        try {
            router.consume(raw);
        } catch (IntegrationViolation exception) {
            log.warn("Rejected invalid AI result event (no payload logged)");
            throw new AmqpRejectAndDontRequeueException("invalid AI result event", exception);
        }
    }
}
