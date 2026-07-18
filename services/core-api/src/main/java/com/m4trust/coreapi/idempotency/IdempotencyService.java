package com.m4trust.coreapi.idempotency;

import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable HTTP idempotency boundary for operations that can create a duplicate
 * side effect.
 *
 * <p>Claim and result recording require the caller's business transaction. A
 * caller that receives a claimed key performs its mutation and then records
 * the stable result reference before committing. A completed result can be
 * safely discovered before an external operation is attempted.
 */
public interface IdempotencyService {

    /**
     * Looks up a completed result before an external operation is attempted.
     * A completed record is immutable, so this does not need the caller's
     * business transaction and deliberately makes replay independent of later
     * resource lifecycle changes.
     */
    Optional<IdempotencyResultReference> findCompleted(
            IdempotencyRequest request);

    @Transactional(propagation = Propagation.MANDATORY)
    IdempotencyClaim claim(IdempotencyRequest request);

    @Transactional(propagation = Propagation.MANDATORY)
    void recordResult(IdempotencyClaim claim,
            IdempotencyResultReference resultReference);
}
