package com.m4trust.coreapi.contracts;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Deterministic ADR-016 §2.5 contract-bundle digest over classpath resources under
 * {@code contracts/{asyncapi,openapi,schemas,examples}/...}.
 */
public final class ContractBundleDigest {

    private static final String CONTRACTS_MARKER = "/contracts/";
    private static final String[] PATTERNS = {
            "classpath*:contracts/asyncapi/**/*.json",
            "classpath*:contracts/asyncapi/**/*.yaml",
            "classpath*:contracts/asyncapi/**/*.yml",
            "classpath*:contracts/openapi/**/*.json",
            "classpath*:contracts/openapi/**/*.yaml",
            "classpath*:contracts/openapi/**/*.yml",
            "classpath*:contracts/schemas/**/*.json",
            "classpath*:contracts/examples/**/*.json"
    };

    private final ResourcePatternResolver resolver;

    public ContractBundleDigest() {
        this(new PathMatchingResourcePatternResolver());
    }

    public ContractBundleDigest(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public record BundleFile(String path, String sha256) {
    }

    public record BundleComputation(String digest, List<BundleFile> files) {
    }

    public BundleComputation compute() {
        try {
            Map<String, byte[]> byPath = new LinkedHashMap<>();
            for (String pattern : PATTERNS) {
                for (Resource resource : resolver.getResources(pattern)) {
                    if (!resource.exists() || !resource.isReadable()) {
                        continue;
                    }
                    String relative = bundleRelativePath(resource);
                    byte[] bytes = readAll(resource);
                    byte[] previous = byPath.putIfAbsent(relative, bytes);
                    if (previous != null && !MessageDigest.isEqual(previous, bytes)) {
                        throw new IllegalStateException(
                                "Conflicting classpath bytes for contract path " + relative);
                    }
                }
            }
            List<String> paths = new ArrayList<>(byPath.keySet());
            paths.sort(Comparator.naturalOrder());
            if (paths.isEmpty()) {
                throw new IllegalStateException("Contract bundle inclusion set is empty");
            }
            StringBuilder manifest = new StringBuilder();
            List<BundleFile> files = new ArrayList<>(paths.size());
            for (String path : paths) {
                String fileDigest = sha256Hex(byPath.get(path));
                manifest.append(fileDigest).append("  ").append(path).append('\n');
                files.add(new BundleFile(path, fileDigest));
            }
            byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
            String digest = "sha256:" + sha256Hex(manifestBytes);
            return new BundleComputation(digest, List.copyOf(files));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to compute contract bundle digest", exception);
        }
    }

    static String bundleRelativePath(Resource resource) throws IOException {
        URI uri = resource.getURI();
        String normalized = uri.toString().replace('\\', '/');
        int marker = normalized.lastIndexOf(CONTRACTS_MARKER);
        if (marker < 0) {
            // classpath:contracts/... without a leading slash before contracts
            int alt = normalized.indexOf("contracts/");
            if (alt < 0) {
                throw new IllegalStateException("Resource is outside contracts/: " + uri);
            }
            return normalized.substring(alt + "contracts/".length());
        }
        return normalized.substring(marker + CONTRACTS_MARKER.length());
    }

    private static byte[] readAll(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
