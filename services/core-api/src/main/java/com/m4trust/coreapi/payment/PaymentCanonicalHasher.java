package com.m4trust.coreapi.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

/** RFC 8785 canonical JSON (JCS), followed by UTF-8 SHA-256 lowercase hex; used for HTTP idempotency hashing. */
@Component
final class PaymentCanonicalHasher {
    String hash(String json) {
        try {
            byte[] canonicalUtf8 = new JsonCanonicalizer(json).getEncodedString().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalUtf8);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | java.io.IOException exception) {
            throw new IllegalStateException("Cannot canonicalize the payment request", exception);
        }
    }
}
