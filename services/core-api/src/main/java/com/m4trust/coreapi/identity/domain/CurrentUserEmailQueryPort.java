package com.m4trust.coreapi.identity.domain;

import java.util.Optional;
import java.util.UUID;

/** Identity-owned normalized email lookup for authenticated user workflows. */
public interface CurrentUserEmailQueryPort {

  Optional<String> findNormalizedEmail(UUID userId);
}
