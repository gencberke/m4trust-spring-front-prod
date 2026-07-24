package com.m4trust.coreapi.deal;

import java.util.Objects;
import java.util.Set;

final class DealLifecycleProjectionCalculator {

    private static final Set<String> KNOWN_FULFILLMENT_STATUSES = Set.of(
            "NOT_STARTED",
            "IN_PROGRESS",
            "EVIDENCE_REQUIRED",
            "REVIEW_REQUIRED",
            "COMPLETED",
            "CANCELLED");

    private DealLifecycleProjectionCalculator() {
    }

    /**
     * Calculates from the authoritative state inputs that exist today.
     *
     * <p>Later slices extend this central calculator when their independent
     * status modules become available; they do not persist or calculate a
     * separate lifecycle field.
     */
    static DealLifecycleProjection calculate(DealStatus dealStatus) {
        return calculate(dealStatus, "NOT_REQUESTED", false, "NOT_CONFIGURED");
    }

    static DealLifecycleProjection calculate(DealStatus dealStatus,
            String analysisStatus, boolean currentDocumentExists) {
        return calculate(dealStatus, analysisStatus, currentDocumentExists, "NOT_CONFIGURED");
    }

    static DealLifecycleProjection calculate(DealStatus dealStatus,
            String analysisStatus, boolean currentDocumentExists, String fundingStatus) {
        return calculate(dealStatus, analysisStatus, currentDocumentExists, fundingStatus, false);
    }

    static DealLifecycleProjection calculate(DealStatus dealStatus,
            String analysisStatus, boolean currentDocumentExists, String fundingStatus,
            boolean activeDisputeForParty) {
        return calculate(dealStatus, analysisStatus, currentDocumentExists, fundingStatus,
                activeDisputeForParty, null);
    }

    static DealLifecycleProjection calculate(DealStatus dealStatus,
            String analysisStatus, boolean currentDocumentExists, String fundingStatus,
            boolean activeDisputeForParty, String fulfillmentStatus) {
        Objects.requireNonNull(dealStatus, "dealStatus must not be null");
        Objects.requireNonNull(analysisStatus, "analysisStatus must not be null");
        Objects.requireNonNull(fundingStatus, "fundingStatus must not be null");
        if (fulfillmentStatus != null && !KNOWN_FULFILLMENT_STATUSES.contains(fulfillmentStatus)) {
            throw new IllegalArgumentException(
                    "Unknown fulfillment status: " + fulfillmentStatus);
        }
        if (dealStatus == DealStatus.ACTIVE && activeDisputeForParty) {
            return DealLifecycleProjection.DISPUTE;
        }
        return switch (dealStatus) {
            case ARCHIVED -> DealLifecycleProjection.ARCHIVED;
            case CANCELLED -> DealLifecycleProjection.CANCELLED;
            case COMPLETED -> DealLifecycleProjection.COMPLETED;
            case ACTIVE -> {
                if ("COMPLETED".equals(fulfillmentStatus)) {
                    yield DealLifecycleProjection.SETTLEMENT;
                }
                yield switch (fundingStatus) {
                    case "NOT_CONFIGURED", "PLANNED", "PENDING", "PARTIALLY_FUNDED" ->
                            DealLifecycleProjection.FUNDING;
                    case "FUNDED" -> DealLifecycleProjection.FULFILLMENT;
                    default -> throw new IllegalArgumentException(
                            "Unknown funding status: " + fundingStatus);
                };
            }
            case DRAFT -> !currentDocumentExists ? DealLifecycleProjection.DRAFT
                    : switch (analysisStatus) {
                        case "QUEUED", "PROCESSING", "FAILED", "NOT_REQUESTED",
                                "SUPERSEDED" -> DealLifecycleProjection.CONTRACT_ANALYSIS;
                        case "REVIEW_REQUIRED" -> DealLifecycleProjection.MANUAL_REVIEW;
                        case "ACCEPTED" -> DealLifecycleProjection.RATIFICATION;
                        default -> throw new IllegalArgumentException(
                                "Unknown analysis status: " + analysisStatus);
                    };
        };
    }
}
