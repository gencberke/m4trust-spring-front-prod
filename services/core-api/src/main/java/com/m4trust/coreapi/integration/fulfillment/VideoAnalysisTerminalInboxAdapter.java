package com.m4trust.coreapi.integration.fulfillment;

import java.util.UUID;

import com.m4trust.coreapi.fulfillment.VideoAnalysisTerminalInboxPort;
import com.m4trust.coreapi.integration.messaging.TransactionalInbox;
import org.springframework.stereotype.Service;

@Service
class VideoAnalysisTerminalInboxAdapter implements VideoAnalysisTerminalInboxPort {

    private final TransactionalInbox inbox;

    VideoAnalysisTerminalInboxAdapter(TransactionalInbox inbox) {
        this.inbox = inbox;
    }

    @Override
    public boolean recordIfNew(UUID eventId, String eventType) {
        return inbox.recordIfNew(eventId, eventType);
    }
}
