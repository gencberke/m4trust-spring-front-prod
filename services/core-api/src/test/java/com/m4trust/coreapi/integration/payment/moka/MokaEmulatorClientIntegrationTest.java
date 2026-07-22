package com.m4trust.coreapi.integration.payment.moka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import com.m4trust.coreapi.payment.PaymentProviderPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the client through the separate Python HTTP emulator process, never an in-Spring stub. */
class MokaEmulatorClientIntegrationTest {
    private Process emulator;
    private MokaHttpPaymentProviderAdapter client;

    @BeforeEach
    void startEmulator() throws Exception {
        int port = availablePort();
        ProcessBuilder process = new ProcessBuilder(pythonExecutable(), "-m", "m4trust_moka_emulator");
        process.directory(java.nio.file.Path.of("..", "..", "tools", "moka-emulator").toFile());
        Map<String, String> environment = process.environment();
        environment.put("M4TRUST_MOKA_EMULATOR_ENABLED", "true");
        environment.put("M4TRUST_MOKA_EMULATOR_PORT", Integer.toString(port));
        environment.put("M4TRUST_MOKA_EMULATOR_SCENARIOS",
                "success,decline,timeout_then_late_success,malformed_error");
        environment.put("PYTHONPATH", "src");
        emulator = process.start();
        waitForHealth(port);
        client = new MokaHttpPaymentProviderAdapter(new MokaTransportSettings(
                URI.create("http://127.0.0.1:" + port), "DEALER-001", "fixture-user", "fixture-password",
                Duration.ofSeconds(1), Duration.ofMillis(400), 8_192, 16_384));
    }

    @AfterEach
    void stopEmulator() {
        if (emulator != null) {
            emulator.destroyForcibly();
        }
    }

    @Test
    void mapsSuccessDeclineMalformedAndLateQueryWithoutProviderLeakage() {
        PaymentProviderPort.ProviderRequest success = request("success-1");
        assertEquals(PaymentProviderPort.Outcome.NOT_FOUND, client.queryStatus(success).outcome());
        PaymentProviderPort.ProviderResult successResult = client.initiate(success);
        assertEquals(PaymentProviderPort.Outcome.SUCCEEDED, successResult.outcome());
        assertEquals("emulator-success-1", successResult.providerReference());
        MokaPoolProbeClient probe = new MokaPoolProbeClient(client);
        assertEquals(MokaHttpPaymentProviderAdapter.PoolProbeOutcome.ACCEPTED_NOT_FINAL,
                probe.approve("success-1").outcome());
        assertEquals(MokaHttpPaymentProviderAdapter.PoolProbeOutcome.ACCEPTED_NOT_FINAL,
                probe.query("success-1").outcome());

        PaymentProviderPort.ProviderResult decline = client.initiate(request("decline-1"));
        assertEquals(PaymentProviderPort.Outcome.DECLINED, decline.outcome());
        assertEquals("emulator-decline-1", decline.providerReference());

        PaymentProviderPort.ProviderResult timeout = client.initiate(request("timeout-1"));
        assertEquals(PaymentProviderPort.Outcome.UNCONFIRMED, timeout.outcome());
        assertNull(timeout.providerReference());
        assertEquals(PaymentProviderPort.Outcome.UNCONFIRMED, client.queryStatus(request("timeout-1")).outcome());
        assertEquals(PaymentProviderPort.Outcome.SUCCEEDED, client.queryStatus(request("timeout-1")).outcome());

        PaymentProviderPort.ProviderResult malformed = client.initiate(request("malformed-1"));
        assertEquals(PaymentProviderPort.Outcome.UNCONFIRMED, malformed.outcome());
        assertNull(malformed.providerReference());
    }

    @Test
    void contradictoryNotFoundIsUnknownAndCannotAuthorizeInitiation() {
        PaymentProviderPort.ProviderResult contradictory = client.parse(
                "{\"IsSuccessful\":true,\"ResultCode\":\"NOT_FOUND\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(PaymentProviderPort.Outcome.UNCONFIRMED, contradictory.outcome());
        assertNull(contradictory.providerReference());
    }

    private static PaymentProviderPort.ProviderRequest request(String key) {
        return new PaymentProviderPort.ProviderRequest(key, 2_750, "TRY");
    }

    private static String pythonExecutable() {
        String override = System.getenv("M4TRUST_PYTHON");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        // Windows Store python3.exe stub is not a real interpreter; prefer python.exe.
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "python"
                : "python3";
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForHealth(int port) throws Exception {
        Instant deadline = Instant.now().plusSeconds(5);
        URI health = URI.create("http://127.0.0.1:" + port + "/health");
        while (Instant.now().isBefore(deadline)) {
            try {
                if (java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder(health).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // The standalone process is still starting.
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Moka emulator did not become healthy");
    }
}
