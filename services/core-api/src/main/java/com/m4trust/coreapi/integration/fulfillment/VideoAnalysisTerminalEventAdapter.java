package com.m4trust.coreapi.integration.fulfillment;

import com.m4trust.coreapi.fulfillment.VideoAnalysisTerminalEventPort;
import com.m4trust.coreapi.fulfillment.VideoAnalysisTerminalViolation;
import com.m4trust.coreapi.integration.messaging.AiTerminalResultHandler;
import com.m4trust.coreapi.integration.messaging.IntegrationViolation;
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
