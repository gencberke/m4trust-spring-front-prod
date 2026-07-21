package com.m4trust.coreapi.fulfillment;

import tools.jackson.databind.JsonNode;

/** Fulfillment-owned boundary for applying validated terminal video-analysis events. */
public interface VideoAnalysisTerminalEventPort {

    void apply(JsonNode event, String eventType);
}
