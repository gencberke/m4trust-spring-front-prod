package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.UUID;

public record DisputeCommentAuthorAttribution(
    UUID legalEntityId, String legalName, String displayName) {}
