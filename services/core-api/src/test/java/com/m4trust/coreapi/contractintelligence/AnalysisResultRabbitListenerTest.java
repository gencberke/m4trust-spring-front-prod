package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class AnalysisResultRabbitListenerTest {

    @Test
    void integrationViolationIsRejectedWithoutExposingTheRawPayload() {
        AnalysisResultConsumer consumer = mock(AnalysisResultConsumer.class);
        String raw = "sensitive-raw-provider-payload";
        doThrow(new AnalysisResultConsumer.IntegrationViolation()).when(consumer).consume(raw);

        AmqpRejectAndDontRequeueException thrown = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> new AnalysisResultRabbitListener(consumer).onMessage(raw));

        assertFalse(thrown.getMessage().contains(raw));
    }
}
