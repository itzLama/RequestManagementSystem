package com.requestmanagement.backend.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masar.attachments.upload")
public class AttachmentUploadProperties {
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
