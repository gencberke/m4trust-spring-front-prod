package com.m4trust.coreapi.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class OutboundJsonMessageFactoryTest {

    @Test
    void createsUtf8JsonMessage() {
        String payload = "{\"title\":\"Sözleşme\"}";

        Message message = OutboundJsonMessageFactory.from(payload);

        assertArrayEquals(payload.getBytes(StandardCharsets.UTF_8), message.getBody());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        assertEquals(StandardCharsets.UTF_8.name(), message.getMessageProperties().getContentEncoding());
    }
}
