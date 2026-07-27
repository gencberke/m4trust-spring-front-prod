package com.m4trust.coreapi.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface IdentityAccountRepository {

  void insert(IdentityAccount account);

  Optional<IdentityAccount> findByNormalizedEmail(String normalizedEmail);

  Optional<String> findNormalizedEmailById(UUID userId);
}
