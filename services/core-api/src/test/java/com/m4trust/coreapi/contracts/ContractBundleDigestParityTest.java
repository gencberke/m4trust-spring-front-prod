package com.m4trust.coreapi.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContractBundleDigestParityTest {

    private static String pythonDigest;

    @BeforeAll
    static void computePythonDigest() throws Exception {
        Path repoRoot = locateRepoRoot();
        Path script = repoRoot.resolve("contracts/scripts/validate_contracts.py");
        assertTrue(Files.isRegularFile(script), "validate_contracts.py must exist");

        ProcessBuilder builder = new ProcessBuilder(
                pythonCommand(),
                script.toString(),
                "--print-digest");
        builder.directory(repoRoot.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.readLine();
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "python digest timed out");
        assertEquals(0, process.exitValue(), "python --print-digest failed: " + output);
        pythonDigest = output == null ? "" : output.trim();
        assertTrue(pythonDigest.startsWith("sha256:"), pythonDigest);
        assertEquals(71, pythonDigest.length(), pythonDigest);
    }

    @Test
    void javaClasspathDigestMatchesPythonReference() {
        ContractBundleDigest.BundleComputation computation = new ContractBundleDigest().compute();
        assertFalse(computation.files().isEmpty());
        assertEquals(pythonDigest, computation.digest());
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : new Path[] {current, current.getParent(), current.getParent().getParent()}) {
            if (candidate != null
                    && Files.isRegularFile(candidate.resolve("contracts/scripts/validate_contracts.py"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate repository root from " + current);
    }

    private static String pythonCommand() {
        String override = System.getenv("M4TRUST_PYTHON");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return "python";
    }
}
