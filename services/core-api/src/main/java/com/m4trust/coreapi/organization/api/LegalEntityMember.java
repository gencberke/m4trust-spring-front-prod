package com.m4trust.coreapi.organization.api;

import com.m4trust.coreapi.organization.domain.*;
import java.util.UUID;

public record LegalEntityMember(
    UUID userId, String email, String displayName, LegalEntityRole role) {}
