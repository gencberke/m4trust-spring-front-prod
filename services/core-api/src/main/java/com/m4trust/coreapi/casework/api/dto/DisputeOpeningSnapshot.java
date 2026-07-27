package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.List;
import java.util.UUID;

public record DisputeOpeningSnapshot(
    UUID ratificationPackageId,
    UUID fulfillmentId,
    String fulfillmentStatusAtOpen,
    long fulfillmentVersionAtOpen,
    UUID milestoneId,
    long milestoneVersionAtOpen,
    List<DisputeEvidenceSnapshotEntry> evidence,
    List<DisputeVideoAnalysisSnapshotEntry> videoAnalysis) {

  public DisputeOpeningSnapshot {
    evidence = List.copyOf(evidence);
    videoAnalysis = List.copyOf(videoAnalysis);
  }
}
