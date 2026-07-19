package com.m4trust.coreapi.contractintelligence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Rabbit boundary: invalid integration traffic is safely dead-lettered, never business-failed. */
@Component
final class AnalysisResultRabbitListener {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultRabbitListener.class);
    private final AnalysisResultConsumer consumer;
    AnalysisResultRabbitListener(AnalysisResultConsumer consumer) { this.consumer = consumer; }

    @RabbitListener(queues = "m4trust.core.ai-results.v1", containerFactory = "aiResultsListenerFactory")
    void onMessage(String raw) {
        try { consumer.consume(raw); }
        catch (AnalysisResultConsumer.IntegrationViolation exception) {
            log.warn("Rejected invalid AI result event (no payload logged)");
            throw new AmqpRejectAndDontRequeueException("invalid AI result event", exception);
        }
    }
}
