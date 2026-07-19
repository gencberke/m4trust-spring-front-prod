package com.m4trust.coreapi.deal;

import java.util.Objects;

import com.m4trust.coreapi.contractintelligence.AnalysisJobStatus;

final class DealLifecycleProjectionCalculator {

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
        return calculate(dealStatus, AnalysisJobStatus.NOT_REQUESTED, false);
    }

    static DealLifecycleProjection calculate(DealStatus dealStatus,
            AnalysisJobStatus analysisStatus, boolean currentDocumentExists) {
        Objects.requireNonNull(dealStatus, "dealStatus must not be null");
        return switch (dealStatus) {
            case ARCHIVED -> DealLifecycleProjection.ARCHIVED;
            case CANCELLED -> DealLifecycleProjection.CANCELLED;
            case COMPLETED -> DealLifecycleProjection.COMPLETED;
            case DRAFT, ACTIVE -> !currentDocumentExists ? DealLifecycleProjection.DRAFT
                    : switch (analysisStatus) {
                        case QUEUED, PROCESSING, FAILED, NOT_REQUESTED -> DealLifecycleProjection.CONTRACT_ANALYSIS;
                        case REVIEW_REQUIRED -> DealLifecycleProjection.MANUAL_REVIEW;
                        case SUPERSEDED -> DealLifecycleProjection.CONTRACT_ANALYSIS;
                    };
        };
    }
}
