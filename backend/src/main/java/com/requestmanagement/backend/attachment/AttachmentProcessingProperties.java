package com.requestmanagement.backend.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("masar.attachments.processing")
public class AttachmentProcessingProperties {
    private boolean automaticScanningEnabled;
    private boolean reconciliationEnabled;
    private boolean destructiveReconciliationEnabled;
    private boolean guestUploadEnabled;
    private int batchSize = 10;
    private Duration interval = Duration.ofSeconds(30);
    private Duration cleanupGracePeriod = Duration.ofMinutes(15);
    private Duration cleanupRetryBackoff = Duration.ofMinutes(5);
    private Duration cleanupClaimTimeout = Duration.ofMinutes(10);
    private int maxCleanupAttempts = 5;
    private int guestUploadsPerHour = 20;

    public boolean isAutomaticScanningEnabled() { return automaticScanningEnabled; }
    public void setAutomaticScanningEnabled(boolean value) { automaticScanningEnabled = value; }
    public boolean isReconciliationEnabled() { return reconciliationEnabled; }
    public void setReconciliationEnabled(boolean value) { reconciliationEnabled = value; }
    public boolean isDestructiveReconciliationEnabled() { return destructiveReconciliationEnabled; }
    public void setDestructiveReconciliationEnabled(boolean value) { destructiveReconciliationEnabled = value; }
    public boolean isGuestUploadEnabled() { return guestUploadEnabled; }
    public void setGuestUploadEnabled(boolean value) { guestUploadEnabled = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration value) { interval = value; }
    public Duration getCleanupGracePeriod() { return cleanupGracePeriod; }
    public void setCleanupGracePeriod(Duration value) { cleanupGracePeriod = value; }
    public Duration getCleanupRetryBackoff() { return cleanupRetryBackoff; }
    public void setCleanupRetryBackoff(Duration value) { cleanupRetryBackoff = value; }
    public Duration getCleanupClaimTimeout() { return cleanupClaimTimeout; }
    public void setCleanupClaimTimeout(Duration value) { cleanupClaimTimeout = value; }
    public int getMaxCleanupAttempts() { return maxCleanupAttempts; }
    public void setMaxCleanupAttempts(int value) { maxCleanupAttempts = value; }
    public int getGuestUploadsPerHour() { return guestUploadsPerHour; }
    public void setGuestUploadsPerHour(int value) { guestUploadsPerHour = value; }
}
