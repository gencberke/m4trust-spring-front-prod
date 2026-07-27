package com.m4trust.coreapi.integration.api.port;

import tools.jackson.databind.JsonNode;

/** Applies one supported terminal AI job result after committed-schema validation. */
public interface AiTerminalResultHandler {

  String jobType();

  void handle(JsonNode event, String eventType);
}
