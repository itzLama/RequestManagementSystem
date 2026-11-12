package com.requestmanagement.backend.attachmentstorage;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentStoragePropertiesTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AttachmentStorageConfiguration.class));

    @Test
    void storageIsDisabledByDefaultAndCreatesNoClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AttachmentObjectStorage.class);
            assertThat(context).doesNotHaveBean("attachmentS3Client");
            assertThat(context.getBean(AttachmentStorageProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void validEnabledConfigurationBindsWithoutContactingStorage() {
        contextRunner.withPropertyValues(
                "masar.attachments.storage.enabled=true",
                "masar.attachments.storage.endpoint=http://127.0.0.1:19000",
                "masar.attachments.storage.region=us-east-1",
                "masar.attachments.storage.bucket=masar-attachment-poc",
                "masar.attachments.storage.access-key=LOCALPOCACCESSKEY",
                "masar.attachments.storage.secret-key=a-long-local-poc-secret",
                "masar.attachments.storage.allow-insecure-http=true",
                "masar.attachments.storage.connection-timeout=1s",
                "masar.attachments.storage.api-call-attempt-timeout=2s",
                "masar.attachments.storage.api-call-timeout=3s",
                "masar.attachments.storage.max-connections=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttachmentObjectStorage.class);
                    AttachmentStorageProperties properties = context.getBean(AttachmentStorageProperties.class);
                    assertThat(properties.getEndpoint()).isEqualTo(URI.create("http://127.0.0.1:19000"));
                    assertThat(properties.getMaxConnections()).isEqualTo(7);
                });
    }

    @Test
    void enabledIncompleteConfigurationFailsContextClearly() {
        contextRunner.withPropertyValues("masar.attachments.storage.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("Attachment storage endpoint is required.");
                });
    }

    @Test
    void enabledConfigurationRejectsMissingCredentialsWithoutRevealingValues() {
        AttachmentStorageProperties properties = validProperties();
        properties.setSecretKey(null);

        assertThatThrownBy(properties::validateForUse)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Attachment storage secret key is required.");
    }

    @Test
    void insecureHttpRequiresExplicitLoopbackConfiguration() {
        AttachmentStorageProperties properties = validProperties();
        properties.setEndpoint(URI.create("http://storage.internal:9000"));
        properties.setAllowInsecureHttp(true);

        assertThatThrownBy(properties::validateForUse)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void configurationStringRedactsCredentials() {
        AttachmentStorageProperties properties = validProperties();

        assertThat(properties.toString())
                .doesNotContain(properties.getAccessKey())
                .doesNotContain(properties.getSecretKey())
                .contains("accessKey=<redacted>", "secretKey=<redacted>");
    }

    private AttachmentStorageProperties validProperties() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setEnabled(true);
        properties.setEndpoint(URI.create("https://storage.example.test"));
        properties.setRegion("us-east-1");
        properties.setBucket("masar-attachments");
        properties.setAccessKey("TESTACCESSKEY");
        properties.setSecretKey("test-secret-value");
        return properties;
    }
}
