package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.time.Instant;
import java.util.UUID;

public record DisputeComment(
    UUID id, String body, DisputeCommentAuthorAttribution authorAttribution, Instant createdAt) {}
