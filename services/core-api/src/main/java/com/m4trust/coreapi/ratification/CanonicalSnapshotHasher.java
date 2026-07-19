package com.m4trust.coreapi.ratification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.erdtman.jcs.JsonCanonicalizer;

/** RFC 8785 canonical JSON (JCS), followed by UTF-8 SHA-256 lowercase hex. */
final class CanonicalSnapshotHasher {
    String hash(String closedSnapshotJson) {
        try {
            byte[] canonicalUtf8 = canonicalize(closedSnapshotJson).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalUtf8);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | java.io.IOException exception) {
            throw new IllegalStateException("Cannot canonicalize the ratification snapshot", exception);
        }
    }

    String canonicalize(String closedSnapshotJson) throws java.io.IOException {
        return new JsonCanonicalizer(closedSnapshotJson).getEncodedString();
    }
}
