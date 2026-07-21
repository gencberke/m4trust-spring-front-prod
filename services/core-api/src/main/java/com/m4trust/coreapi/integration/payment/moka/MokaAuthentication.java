package com.m4trust.coreapi.integration.payment.moka;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Moka's documented per-request authentication material construction. */
final class MokaAuthentication {

    private MokaAuthentication() {
    }

    static String checkKey(String dealerCode, String username, String password) {
        String input = dealerCode + "MK" + username + "PD" + password;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
