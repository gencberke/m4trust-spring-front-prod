package com.m4trust.coreapi.integration.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import com.m4trust.coreapi.fulfillment.FulfillmentObjectStorage;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3-compatible adapter for fulfillment evidence objects. */
class S3FulfillmentObjectStorage implements FulfillmentObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;
    private final Clock clock;

    S3FulfillmentObjectStorage(S3Client client, S3Presigner presigner,
            ObjectStorageProperties properties, Clock clock) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public DirectUpload createDirectUpload(String objectKey, String mediaType, long contentLength) {
        if (contentLength <= 0 || contentLength > properties.maxUploadSizeBytes()) {
            throw new IllegalArgumentException("evidence upload size is outside configured bounds");
        }
        PutObjectRequest request = PutObjectRequest.builder().bucket(properties.bucket())
                .key(objectKey).contentType(mediaType).contentLength(contentLength).build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadTtl()).putObjectRequest(request).build());
        return new DirectUpload(URI.create(signed.url().toString()), browserHeaders(signed.signedHeaders()),
                clock.instant().plus(properties.uploadTtl()));
    }

    @Override
    public DirectDownload createDirectDownload(String objectKey, String objectVersion) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(properties.bucket())
                .key(objectKey).versionId(objectVersion).build();
        PresignedGetObjectRequest signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadTtl()).getObjectRequest(request).build());
        return new DirectDownload(URI.create(signed.url().toString()), clock.instant().plus(properties.downloadTtl()));
    }

    @Override
    public VerifiedObject verify(String objectKey) {
        HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
                .bucket(properties.bucket()).key(objectKey).build());
        String version = requireVersion(head.versionId());
        String mediaType = requireMediaType(head.contentType());
        String nativeChecksum = head.checksumSHA256();
        if (nativeChecksum != null && !nativeChecksum.isBlank()) {
            return new VerifiedObject(head.contentLength(), decodeChecksum(nativeChecksum), version, mediaType);
        }
        return hashObject(objectKey, version, mediaType, head.contentLength());
    }

    private VerifiedObject hashObject(String objectKey, String version, String mediaType, long expectedSize) {
        MessageDigest digest = sha256();
        long actualSize = 0;
        try (ResponseInputStream<GetObjectResponse> object = client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(objectKey).versionId(version).build())) {
            actualSize = copyAndDigest(object, digest);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to verify evidence object", exception);
        }
        if (actualSize != expectedSize) {
            throw new IllegalStateException("object size changed during verification");
        }
        return new VerifiedObject(actualSize, HexFormat.of().formatHex(digest.digest()), version, mediaType);
    }

    private static long copyAndDigest(InputStream input, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        for (int read; (read = input.read(buffer)) != -1;) {
            digest.update(buffer, 0, read);
            total += read;
        }
        return total;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String decodeChecksum(String checksum) {
        return HexFormat.of().formatHex(Base64.getDecoder().decode(checksum));
    }

    private static String requireVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("object storage must return an immutable object version");
        }
        return value;
    }

    private static String requireMediaType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("object storage must return a content type for media-type verification");
        }
        return value;
    }

    private static Map<String, String> browserHeaders(Map<String, java.util.List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name) && !values.isEmpty()) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }
}
