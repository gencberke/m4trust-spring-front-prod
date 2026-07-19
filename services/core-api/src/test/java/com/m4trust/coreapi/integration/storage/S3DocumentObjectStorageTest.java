package com.m4trust.coreapi.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.m4trust.coreapi.document.DocumentObjectStorage.VerifiedObject;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3DocumentObjectStorageTest {

    @Test
    void verificationHashesVersionPinnedBytesWhenProviderDoesNotReturnChecksum() {
        HeadObjectResponse head = HeadObjectResponse.builder().contentLength(3L)
                .versionId("immutable-version").build();
        S3Client client = (S3Client) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {S3Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("headObject")) {
                        return head;
                    }
                    if (method.getName().equals("getObject")) {
                        return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                                new ByteArrayInputStream("abc".getBytes()));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        S3DocumentObjectStorage storage = new S3DocumentObjectStorage(client, null,
                properties(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        VerifiedObject verified = storage.verify("documents/random");

        assertThat(verified.sizeBytes()).isEqualTo(3);
        assertThat(verified.sha256()).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(verified.objectVersion()).isEqualTo("immutable-version");
    }

    @Test
    void verificationUsesProviderChecksumWhenItIsAuthoritative() {
        HeadObjectResponse head = HeadObjectResponse.builder().contentLength(3L)
                .versionId("immutable-version").checksumSHA256(
                        "ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qc tBD/YfI AFa0=".replace(" ", ""))
                .build();
        S3Client client = (S3Client) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {S3Client.class}, (proxy, method, args) -> {
                    if (method.getName().equals("headObject")) {
                        return head;
                    }
                    throw new AssertionError("byte fallback must not run when checksum is present");
                });

        S3DocumentObjectStorage storage = new S3DocumentObjectStorage(client, null,
                properties(), Clock.systemUTC());

        assertThat(storage.verify("documents/random").sha256()).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private static ObjectStorageProperties properties() {
        return new ObjectStorageProperties(URI.create("http://127.0.0.1:9000"), "us-east-1",
                "m4trust-documents", "access", "secret", Duration.ofMinutes(10),
                Duration.ofMinutes(5), Duration.ofMinutes(15), 52_428_800);
    }
}
