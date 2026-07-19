package com.m4trust.coreapi.ratification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanonicalSnapshotHasherTest {
    private final CanonicalSnapshotHasher hasher = new CanonicalSnapshotHasher();

    @Test
    void appliesTheRfc8785Section322SerializationSample() throws Exception {
        String source = "{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001],\"string\":\"\\u20ac$\\u000f\\nA'B\\\"\\\\\\\"/\"}";
        String expected = "{\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\"/\"}";
        assertEquals(expected, hasher.canonicalize(source));
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
