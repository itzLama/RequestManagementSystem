package com.requestmanagement.backend.attachment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentScanningProperties.class)
class AttachmentScanningConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "masar.attachments.scanning", name = "enabled", havingValue = "true")
    AttachmentMalwareScanner attachmentMalwareScanner(AttachmentScanningProperties properties) {
        properties.validate();
        return new ClamAvInstreamScanner(properties);
    }
}
