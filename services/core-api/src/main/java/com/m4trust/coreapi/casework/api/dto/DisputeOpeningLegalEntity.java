package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.UUID;

public record DisputeOpeningLegalEntity(UUID legalEntityId, String legalName) {}
