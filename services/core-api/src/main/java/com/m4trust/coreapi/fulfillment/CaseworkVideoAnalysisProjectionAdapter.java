package com.m4trust.coreapi.fulfillment;

import com.m4trust.coreapi.casework.CaseworkSourcePorts;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Fulfillment-owned safe advisory projection for pinned dispute video results. */
@Service
class CaseworkVideoAnalysisProjectionAdapter implements CaseworkSourcePorts.VideoAnalysisProjection {

    private final VideoAnalysisRepository repository;
    private final ObjectMapper objectMapper;

    CaseworkVideoAnalysisProjectionAdapter(VideoAnalysisRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object readPinnedPublicResult(UUID jobId, UUID resultId) {
        UUID storedResultId = repository.findResultIdByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Pinned video result is unavailable"));
        if (!storedResultId.equals(resultId)) {
            throw new IllegalStateException("Pinned video result identity does not match");
        }
        String serialized = repository.findResultByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("Pinned video result payload is unavailable"));
        return readPublicResult(serialized);
    }

    private Object readPublicResult(String serialized) {
        try {
            var canonical = objectMapper.readTree(serialized);
            var result = canonical.has("result") ? canonical.get("result") : canonical;
            var warnings = canonical.has("warnings")
                    ? canonical.get("warnings")
                    : canonical.path("warnings");
            if (!warnings.isArray()) {
                warnings = objectMapper.createArrayNode();
            }
            return java.util.Map.of(
                    "durationMs", result.get("durationMs").numberValue(),
                    "observations", objectMapper.treeToValue(result.get("observations"), Object.class),
                    "anomalies", objectMapper.treeToValue(result.get("anomalies"), Object.class),
                    "summary", objectMapper.treeToValue(result.get("summary"), Object.class),
                    "warnings", objectMapper.treeToValue(warnings, Object.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored canonical result is invalid", exception);
        }
    }
}
