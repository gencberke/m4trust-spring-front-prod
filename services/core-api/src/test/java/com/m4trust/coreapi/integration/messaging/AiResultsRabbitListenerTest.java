package com.m4trust.coreapi.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class AiResultsRabbitListenerTest {

    @Test
    void integrationViolationIsRejectedWithoutExposingTheRawPayload() {
        AiResultsMessageRouter router = mock(AiResultsMessageRouter.class);
        String raw = "sensitive-raw-provider-payload";
        doThrow(new IntegrationViolation()).when(router).consume(raw);

        AmqpRejectAndDontRequeueException thrown = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> new AiResultsRabbitListener(router).onMessage(raw));

        assertFalse(thrown.getMessage().contains(raw));
    }
}
