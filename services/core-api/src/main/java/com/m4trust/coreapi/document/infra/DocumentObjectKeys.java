package com.m4trust.coreapi.document.infra;

import com.m4trust.coreapi.document.domain.*;
import java.util.UUID;

/** Generates opaque storage names; user-supplied file names never become object keys. */
public final class DocumentObjectKeys {

  private DocumentObjectKeys() {}

  public static String newKey() {
    return "documents/" + UUID.randomUUID();
  }
}
