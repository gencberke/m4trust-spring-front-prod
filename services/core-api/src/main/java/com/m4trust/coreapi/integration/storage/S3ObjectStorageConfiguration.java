package com.m4trust.coreapi.integration.storage;

import com.m4trust.coreapi.document.DocumentObjectStorage;
import com.m4trust.coreapi.fulfillment.FulfillmentObjectStorage;
import java.net.URI;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageProperties.class)
class S3ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(ObjectStorageProperties properties) {
        return S3Client.builder().endpointOverride(properties.endpoint())
                .region(Region.of(properties.region())).credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(ObjectStorageProperties properties) {
        return S3Presigner.builder().endpointOverride(properties.endpoint())
                .region(Region.of(properties.region())).credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }

    @Bean
    DocumentObjectStorage documentObjectStorage(S3Client client, S3Presigner presigner,
            ObjectStorageProperties properties, Clock clock) {
        return new S3DocumentObjectStorage(client, presigner, properties, clock);
    }

    @Bean
    FulfillmentObjectStorage fulfillmentObjectStorage(S3Client client, S3Presigner presigner,
            ObjectStorageProperties properties, Clock clock) {
        return new S3FulfillmentObjectStorage(client, presigner, properties, clock);
    }

    private static StaticCredentialsProvider credentials(ObjectStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.accessKey(), properties.secretKey()));
    }
}
