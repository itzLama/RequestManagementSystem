package com.requestmanagement.backend.attachment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AttachmentProcessingProperties.class)
class AttachmentProcessingConfiguration { }
