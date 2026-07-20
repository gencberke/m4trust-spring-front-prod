package com.m4trust.coreapi.fulfillment;

import java.util.UUID;

/**
 * Fulfillment-owned technical boundary for enqueueing contract-valid video analysis
 * commands in the caller's active transaction.
 */
public interface VideoAnalysisCommandEnqueuePort {

    String REQUESTED_EVENT_TYPE = "ai.job.requested.v1";
    String COMMANDS_EXCHANGE = "m4trust.ai.commands";
    String REQUESTED_ROUTING_KEY = "ai.video-analysis.requested.v1";

    UUID enqueueRequested(String serializedEvent);
}
