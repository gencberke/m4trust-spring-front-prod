package com.m4trust.coreapi.deal.domain;

public final class DealStateConflictException extends RuntimeException {

  public DealStateConflictException(String message) {
    super(message);
  }
}
