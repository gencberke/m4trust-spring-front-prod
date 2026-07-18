package com.m4trust.coreapi.document;

import java.util.UUID;

/** Generates opaque storage names; user-supplied file names never become object keys. */
public final class DocumentObjectKeys {

    private DocumentObjectKeys() {
    }

    public static String newKey() {
        return "documents/" + UUID.randomUUID();
    }
}
