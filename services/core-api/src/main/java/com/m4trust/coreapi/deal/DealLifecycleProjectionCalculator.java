package com.m4trust.coreapi.deal;

import java.util.Objects;

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
        Objects.requireNonNull(dealStatus, "dealStatus must not be null");
        return switch (dealStatus) {
            case ARCHIVED -> DealLifecycleProjection.ARCHIVED;
            case CANCELLED -> DealLifecycleProjection.CANCELLED;
            case COMPLETED -> DealLifecycleProjection.COMPLETED;
            case DRAFT, ACTIVE -> DealLifecycleProjection.DRAFT;
        };
    }
}
