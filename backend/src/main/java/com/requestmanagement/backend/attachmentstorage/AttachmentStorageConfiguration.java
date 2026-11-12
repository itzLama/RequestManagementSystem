package com.requestmanagement.backend.attachmentstorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentStorageProperties.class)
public class AttachmentStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "masar.attachments.storage", name = "enabled", havingValue = "true")
    S3Client attachmentS3Client(AttachmentStorageProperties properties) {
        properties.validateForUse();
        return S3Client.builder()
                .endpointOverride(properties.getEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build())
                .httpClientBuilder(Apache5HttpClient.builder()
                        .connectionTimeout(properties.getConnectionTimeout())
                        .socketTimeout(properties.getApiCallAttemptTimeout())
                        .maxConnections(properties.getMaxConnections()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                        .apiCallTimeout(properties.getApiCallTimeout())
                        .build())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "masar.attachments.storage", name = "enabled", havingValue = "true")
    AttachmentObjectStorage attachmentObjectStorage(
            S3Client attachmentS3Client,
            AttachmentStorageProperties properties
    ) {
        return new S3AttachmentObjectStorage(attachmentS3Client, properties.getBucket());
    }
}
