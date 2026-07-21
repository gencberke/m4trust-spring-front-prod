package com.m4trust.coreapi.integration.payment.moka;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import com.m4trust.coreapi.payment.PaymentProviderPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bounded, provider-neutral Moka HTTP adapter. It neither logs nor returns raw
 * upstream bodies, credentials, CheckKey values, or provider messages.
 */
public final class MokaHttpPaymentProviderAdapter implements PaymentProviderPort {
    private static final String INITIATE_PATH = "/PaymentDealer/DoDirectPayment";
    private static final String QUERY_PATH = "/PaymentDealer/GetDealerPaymentTrxDetailList";
    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final MokaTransportSettings settings;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MokaHttpPaymentProviderAdapter(MokaTransportSettings settings) {
        this(settings, HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build(), new ObjectMapper());
    }

    MokaHttpPaymentProviderAdapter(MokaTransportSettings settings, HttpClient httpClient, ObjectMapper objectMapper) {
        this.settings = settings;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderResult initiate(ProviderRequest request) {
        return exchange(INITIATE_PATH, Map.of(
                "PaymentDealerAuthentication", authentication(),
                "OtherTrxCode", request.providerKey(),
                "Amount", MokaMoney.decimalMajor(request.amountMinor(), request.currency()),
                "Currency", request.currency(),
                "IsPoolPayment", 1));
    }

    @Override
    public ProviderResult queryStatus(ProviderRequest request) {
        return exchange(QUERY_PATH, Map.of(
                "PaymentDealerAuthentication", authentication(),
                "OtherTrxCode", request.providerKey()));
    }

    private Map<String, String> authentication() {
        return Map.of(
                "DealerCode", settings.dealerCode(),
                "Username", settings.username(),
                "Password", settings.password(),
                "CheckKey", MokaAuthentication.checkKey(settings.dealerCode(), settings.username(), settings.password()));
    }

    private ProviderResult exchange(String path, Map<String, ?> payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            if (body.length > settings.maxRequestBytes()) {
                return unknown();
            }
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(settings.readTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                close(response.body());
                return unknown();
            }
            return parse(readBounded(response.body()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return unknown();
        }
    }

    private ProviderResult parse(byte[] responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isObject() || !root.path("IsSuccessful").isBoolean()
                    || !root.path("ResultCode").isTextual()) {
                return unknown();
            }
            String code = root.path("ResultCode").textValue();
            if ("NOT_FOUND".equals(code)) {
                return new ProviderResult(Outcome.NOT_FOUND, null);
            }
            String reference = safeReference(root.path("VirtualPosOrderId").textValue());
            if (reference == null) {
                reference = safeReference(root.path("OtherTrxCode").textValue());
            }
            if (root.path("IsSuccessful").booleanValue() && "SUCCESS".equals(code) && reference != null) {
                return new ProviderResult(Outcome.SUCCEEDED, reference);
            }
            if (!root.path("IsSuccessful").booleanValue() && "DECLINED".equals(code) && reference != null) {
                return new ProviderResult(Outcome.DECLINED, reference);
            }
            return unknown();
        } catch (JacksonException exception) {
            return unknown();
        }
    }

    private byte[] readBounded(InputStream stream) throws IOException {
        try (stream) {
            byte[] bytes = stream.readNBytes(settings.maxResponseBytes() + 1);
            if (bytes.length > settings.maxResponseBytes()) {
                throw new IOException("response exceeds configured bound");
            }
            return bytes;
        }
    }

    private static void close(InputStream stream) {
        try (stream) {
            // Close an ignored body without exposing it to logs or callers.
        } catch (IOException ignored) {
            // Stable unknown outcome is safer than propagating upstream details.
        }
    }

    private URI resolve(String path) {
        return settings.baseUri().resolve(path);
    }

    private static String safeReference(String candidate) {
        return candidate != null && SAFE_REFERENCE.matcher(candidate).matches() ? candidate : null;
    }

    private static ProviderResult unknown() {
        return new ProviderResult(Outcome.UNCONFIRMED, null);
    }
}
