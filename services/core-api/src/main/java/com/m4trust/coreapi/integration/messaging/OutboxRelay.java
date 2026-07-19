package com.m4trust.coreapi.integration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.messaging.relay", name = "enabled", havingValue = "true")
class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcOutboxRelayStore store;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxRelayProperties properties;

    OutboxRelay(JdbcOutboxRelayStore store, RabbitTemplate rabbitTemplate,
            OutboxRelayProperties properties) {
        this.store = store;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.messaging.relay.fixed-delay}")
    void relay() {
        for (OutboxClaim claim : store.claimAvailable(properties.batchSize(),
                properties.claimTimeout())) {
            try {
                publishWithConfirm(claim);
                store.markPublished(claim);
            } catch (RuntimeException exception) {
                // The claim expires and is retried. A broker-confirmed message can therefore repeat.
                LOGGER.warn("outbox publish deferred eventId={} routingKey={}", claim.id(),
                        claim.routingKey(), exception);
            }
        }
    }

    private void publishWithConfirm(OutboxClaim claim) {
        rabbitTemplate.invoke(operations -> {
            operations.send(claim.exchangeName(), claim.routingKey(),
                    OutboundJsonMessageFactory.from(claim.payload()));
            operations.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
            return null;
        });
    }
}
