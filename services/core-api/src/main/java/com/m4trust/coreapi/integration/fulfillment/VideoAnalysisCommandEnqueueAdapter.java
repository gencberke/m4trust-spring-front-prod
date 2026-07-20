package com.m4trust.coreapi.integration.fulfillment;

import java.util.UUID;

import com.m4trust.coreapi.fulfillment.VideoAnalysisCommandEnqueuePort;
import com.m4trust.coreapi.integration.messaging.TransactionalOutbox;
import org.springframework.stereotype.Service;

@Service
class VideoAnalysisCommandEnqueueAdapter implements VideoAnalysisCommandEnqueuePort {

    private final TransactionalOutbox outbox;

    VideoAnalysisCommandEnqueueAdapter(TransactionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public UUID enqueueRequested(String serializedEvent) {
        return outbox.enqueue(REQUESTED_EVENT_TYPE, COMMANDS_EXCHANGE, REQUESTED_ROUTING_KEY,
                serializedEvent);
    }
}
