package com.m4trust.coreapi.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

class AsyncApiTopologyTest {

    private final AsyncApiTopology topology = new AsyncApiTopology();

    @Test
    void durableQueuesAndBindingsMatchCommittedAsyncApi() {
        Queue documentQueue = topology.documentExtractionQueue();
        Queue resultsQueue = topology.resultsQueue();
        Binding documentBinding = topology.documentExtractionBinding(documentQueue,
                topology.commandsExchange());
        Set<String> resultRoutingKeys = Set.of(
                topology.documentCompletedBinding(resultsQueue, topology.eventsExchange()).getRoutingKey(),
                topology.documentFailedBinding(resultsQueue, topology.eventsExchange()).getRoutingKey(),
                topology.videoCompletedBinding(resultsQueue, topology.eventsExchange()).getRoutingKey(),
                topology.videoFailedBinding(resultsQueue, topology.eventsExchange()).getRoutingKey());
        Binding deadLetterBinding = topology.deadLetterBinding(topology.deadLetterQueue(),
                topology.deadLetterExchange());

        assertTrue(documentQueue.isDurable());
        assertEquals(AsyncApiTopology.DEAD_LETTER_EXCHANGE,
                documentQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(AsyncApiTopology.COMMANDS_EXCHANGE, documentBinding.getExchange());
        assertEquals("ai.document-extraction.requested.v1", documentBinding.getRoutingKey());
        assertEquals(Set.of("ai.document-extraction.completed.v1",
                "ai.document-extraction.failed.v1", "ai.video-analysis.completed.v1",
                "ai.video-analysis.failed.v1"), resultRoutingKeys);
        assertEquals(AsyncApiTopology.DEAD_LETTER_EXCHANGE, deadLetterBinding.getExchange());
        assertEquals("#", deadLetterBinding.getRoutingKey());
    }
}
