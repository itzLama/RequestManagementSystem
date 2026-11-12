package com.requestmanagement.backend.attachmentstorage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUSTFS_INTEGRATION_TEST", matches = "true")
class RustFsAttachmentObjectStorageIntegrationTests {
    @Test
    void privateRustFsLifecycleUsesStreamingAdapter() {
        AttachmentStorageProperties properties = propertiesFromEnvironment();
        AttachmentStorageConfiguration configuration = new AttachmentStorageConfiguration();
        AttachmentObjectKeys keys = new AttachmentObjectKeyGenerator().generate();
        byte[] content = "Masar backend storage integration probe".getBytes(StandardCharsets.UTF_8);

        try (S3Client client = configuration.attachmentS3Client(properties)) {
            AttachmentObjectStorage storage = new S3AttachmentObjectStorage(client, properties.getBucket());
            assertThat(storage.diagnostic().reachable()).isTrue();

            storage.uploadQuarantine(keys.quarantine(), new ByteArrayInputStream(content), content.length,
                    "text/plain", Map.of("poc", "backend-foundation"));
            assertThat(storage.exists(keys.quarantine())).isTrue();
            assertThat(storage.metadata(keys.quarantine()).contentLength()).isEqualTo(content.length);

            try (StoredAttachmentObject stored = storage.open(keys.quarantine())) {
                assertThat(stored.stream().readAllBytes()).containsExactly(content);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not close the integration download stream.", exception);
            }

            storage.promote(keys.quarantine(), keys.available());
            assertThat(storage.exists(keys.available())).isTrue();
            storage.delete(keys.quarantine());
            storage.delete(keys.available());
            assertThat(storage.exists(keys.quarantine())).isFalse();
            assertThat(storage.exists(keys.available())).isFalse();
        }
    }

    private AttachmentStorageProperties propertiesFromEnvironment() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setEnabled(true);
        properties.setEndpoint(URI.create(required("RUSTFS_POC_ENDPOINT")));
        properties.setRegion(required("RUSTFS_POC_REGION"));
        properties.setBucket(required("RUSTFS_POC_BUCKET"));
        properties.setAccessKey(required("RUSTFS_POC_ACCESS_KEY"));
        properties.setSecretKey(required("RUSTFS_POC_SECRET_KEY"));
        properties.setAllowInsecureHttp(true);
        properties.setConnectionTimeout(Duration.ofSeconds(2));
        properties.setApiCallAttemptTimeout(Duration.ofSeconds(5));
        properties.setApiCallTimeout(Duration.ofSeconds(10));
        properties.validateForUse();
        return properties;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }
}
