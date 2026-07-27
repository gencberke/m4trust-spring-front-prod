package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;

public record DisputeAvailableActions(
    boolean canComment, boolean canAcknowledge, boolean canWithdraw) {}
