package com.m4trust.coreapi.identity.domain;

public final class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid credentials.");
  }
}
