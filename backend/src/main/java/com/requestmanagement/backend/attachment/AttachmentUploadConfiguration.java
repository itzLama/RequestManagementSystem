package com.requestmanagement.backend.attachment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentUploadProperties.class)
class AttachmentUploadConfiguration {
}
