package com.m4trust.coreapi.fulfillment;

import java.util.UUID;

/** Fulfillment-owned inbox boundary for terminal video-analysis events. */
public interface VideoAnalysisTerminalInboxPort {

    boolean recordIfNew(UUID eventId, String eventType);
}
