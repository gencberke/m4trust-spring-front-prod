package com.m4trust.coreapi.deal.domain;

public enum DealLifecycleProjection {
  DRAFT,
  CONTRACT_ANALYSIS,
  MANUAL_REVIEW,
  RATIFICATION,
  FUNDING,
  FULFILLMENT,
  SETTLEMENT,
  DISPUTE,
  COMPLETED,
  CANCELLED,
  ARCHIVED
}
