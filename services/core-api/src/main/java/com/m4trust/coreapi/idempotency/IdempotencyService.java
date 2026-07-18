package com.m4trust.coreapi.idempotency;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable HTTP idempotency boundary for operations that can create a duplicate
 * side effect.
 *
 * <p>Both methods require the caller's business transaction. A caller that
 * receives a claimed key performs its mutation and then records the stable
 * result reference before committing. A replaying caller uses the returned
 * reference to load an equivalent response from its owning module.
 */
public interface IdempotencyService {

    @Transactional(propagation = Propagation.MANDATORY)
    IdempotencyClaim claim(IdempotencyRequest request);

    @Transactional(propagation = Propagation.MANDATORY)
    void recordResult(IdempotencyClaim claim,
            IdempotencyResultReference resultReference);
}
