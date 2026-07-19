package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanonicalSnapshotHasherTest {
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();

    @Test
    void appliesTheRfc8785ObjectMemberOrderingVector() throws Exception {
        // RFC 8785 JCS sorts object properties lexicographically, independently of input order.
        assertEquals("{\"a\":1,\"b\":2}", hasher.canonicalize("{\"b\":2,\"a\":1}"));
    }

    @Test
    void hashesSemanticallyEquivalentMemberOrderWithLowercaseSha256() {
        String first = hasher.hash("{\"b\":2,\"a\":1}");
        String second = hasher.hash("{\"a\":1,\"b\":2}");
        assertEquals("43258cff783fe7036d8a43033f830adfc60ec037382473548ac742b888292777", first);
        assertEquals(first, second);
        assertTrue(first.matches("[a-f0-9]{64}"));
    }
}
