package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

record AcceptDealInvitationRequest(
    @NotNull(message = "Legal entity is required.") UUID legalEntityId,
    @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must not be negative.")
        Long expectedVersion) {

  long version() {
    return expectedVersion;
  }
}
