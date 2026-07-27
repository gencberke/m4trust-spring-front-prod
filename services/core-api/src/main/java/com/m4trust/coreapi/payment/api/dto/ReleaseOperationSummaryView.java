package com.m4trust.coreapi.payment.api.dto;

import com.m4trust.coreapi.payment.domain.ReleaseOperationStatus;
import java.util.UUID;

public record ReleaseOperationSummaryView(UUID id, ReleaseOperationStatus status) {}
