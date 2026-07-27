package com.m4trust.coreapi.fulfillment.domain.port;

import com.m4trust.coreapi.fulfillment.domain.*;
import java.util.UUID;

/** Fulfillment-owned inbox boundary for terminal video-analysis events. */
public interface VideoAnalysisTerminalInboxPort {

  boolean recordIfNew(UUID eventId, String eventType);
}
