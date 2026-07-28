package com.m4trust.coreapi.casework.domain;

/** Domain-owned conflict reasons. Mapped exhaustively to ApiErrorCode at the API boundary. */
public enum CaseworkConflictReason {
  DEAL_STATE_CONFLICT,
  DEAL_STALE_VERSION,
  FULFILLMENT_STATE_CONFLICT,
  FULFILLMENT_STALE_VERSION,
  DISPUTE_STATE_CONFLICT,
  DISPUTE_STALE_VERSION,
  DISPUTE_ACTIVE_CASE_EXISTS
}
