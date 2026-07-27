package com.m4trust.coreapi.identity.domain;

public final class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException() {
    super("Normalized email is already registered.");
  }
}
