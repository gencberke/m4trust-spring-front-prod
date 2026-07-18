package com.m4trust.coreapi.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class JdbcIdempotencyService implements IdempotencyService {

    private final IdempotencyRepository repository;

    JdbcIdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyClaim claim(IdempotencyRequest request) {
        UUID recordId = UUID.randomUUID();
        if (repository.insertIfAbsent(recordId, request, Instant.now())) {
            return new IdempotencyClaim(recordId,
                    IdempotencyClaimStatus.CLAIMED, null);
        }

        IdempotencyRecord existing = repository.find(request)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record disappeared after a unique-key conflict"));
        if (!existing.canonicalRequestHash().equals(
                request.canonicalRequestHash())) {
            throw new IdempotencyKeyReusedException();
        }
        return new IdempotencyClaim(existing.id(), IdempotencyClaimStatus.REPLAY,
                existing.resultReference().orElseThrow(() ->
                        new IllegalStateException(
                                "Committed idempotency record has no result reference")));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordResult(IdempotencyClaim claim,
            IdempotencyResultReference resultReference) {
        if (claim.status() != IdempotencyClaimStatus.CLAIMED) {
            throw new IllegalArgumentException(
                    "Only a newly claimed idempotency key can record a result");
        }
        if (!repository.recordResult(claim.recordId(), resultReference)) {
            throw new IllegalStateException(
                    "Idempotency result could not be recorded for the claimed key");
        }
    }
}
