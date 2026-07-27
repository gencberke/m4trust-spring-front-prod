package com.m4trust.coreapi.integration.infra.fulfillment;

import com.m4trust.coreapi.fulfillment.domain.VideoAnalysisTerminalViolation;
import com.m4trust.coreapi.fulfillment.domain.port.VideoAnalysisTerminalEventPort;
import com.m4trust.coreapi.integration.api.port.AiTerminalResultHandler;
import com.m4trust.coreapi.integration.infra.messaging.IntegrationViolation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
class VideoAnalysisTerminalEventAdapter implements AiTerminalResultHandler {

  private static final String JOB_TYPE = "VIDEO_ANALYSIS";

  private final VideoAnalysisTerminalEventPort terminalEvents;

  VideoAnalysisTerminalEventAdapter(VideoAnalysisTerminalEventPort terminalEvents) {
    this.terminalEvents = terminalEvents;
  }

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void handle(JsonNode event, String eventType) {
    try {
      terminalEvents.apply(event, eventType);
    } catch (VideoAnalysisTerminalViolation exception) {
      throw new IntegrationViolation();
    }
  }
}
