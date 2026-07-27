package com.m4trust.coreapi.fulfillment.domain;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates unpredictable object keys for evidence storage. */
public final class FulfillmentObjectKeys {

  private static final SecureRandom RANDOM = new SecureRandom();

  private FulfillmentObjectKeys() {}

  public static String newKey() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
