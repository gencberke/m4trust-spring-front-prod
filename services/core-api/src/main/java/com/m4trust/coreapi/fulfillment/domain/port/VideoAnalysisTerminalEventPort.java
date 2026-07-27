package com.m4trust.coreapi.fulfillment.domain.port;

import com.m4trust.coreapi.fulfillment.domain.*;
import tools.jackson.databind.JsonNode;

/** Fulfillment-owned boundary for applying validated terminal video-analysis events. */
public interface VideoAnalysisTerminalEventPort {

  void apply(JsonNode event, String eventType);
}
