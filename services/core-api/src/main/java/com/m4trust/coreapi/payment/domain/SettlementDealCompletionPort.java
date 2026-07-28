package com.m4trust.coreapi.payment.domain;

import java.util.UUID;

/**
 * Narrow port for payment-owned Deal completion after query-verified simulated settlement.
 * Implemented by the deal module.
 */
public interface SettlementDealCompletionPort {

  /**
   * Atomically transitions an ACTIVE Deal to COMPLETED when called inside the settlement
   * result-application transaction.
   *
   * @return false when the Deal version changed concurrently
   */
  boolean complete(UUID dealId, long expectedVersion);
}
