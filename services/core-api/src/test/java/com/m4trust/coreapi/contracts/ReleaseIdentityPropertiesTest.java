package com.m4trust.coreapi.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleaseIdentityPropertiesTest {

    @Test
    void acceptsExactFortyHex() {
        ReleaseIdentityProperties properties = new ReleaseIdentityProperties(
                "0123456789ABCDEF0123456789ABCDEF01234567");
        assertEquals("0123456789abcdef0123456789abcdef01234567",
                properties.releaseRevision());
    }

    @Test
    void rejectsBlankInsteadOfFortyZeros() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ReleaseIdentityProperties(""));
        assertTrue(thrown.getMessage().contains("fail closed"));
        assertTrue(thrown.getMessage().contains("forty zeros"));
    }

    @Test
    void rejectsMalformedInsteadOfFortyZeros() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ReleaseIdentityProperties("unknown"));
        assertTrue(thrown.getMessage().contains("fail closed"));
        assertTrue(thrown.getMessage().contains("forty zeros"));
    }

    @Test
    void rejectsLiteralFortyZeros() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReleaseIdentityProperties("0".repeat(40)));
    }
}
