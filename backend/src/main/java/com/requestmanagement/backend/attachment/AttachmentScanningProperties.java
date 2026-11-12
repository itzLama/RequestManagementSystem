package com.requestmanagement.backend.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("masar.attachments.scanning")
public class AttachmentScanningProperties {
    private boolean enabled;
    private String host = "127.0.0.1";
    private int port = 13310;
    private Duration connectionTimeout = Duration.ofSeconds(2);
    private Duration scanTimeout = Duration.ofSeconds(60);
    private int maxConcurrentScans = 2;
    private int maxAttempts = 3;
    private Duration retryBackoff = Duration.ofMinutes(1);
    private Duration claimTimeout = Duration.ofMinutes(10);
    private long maxStreamBytes = 25L * 1024 * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration value) { connectionTimeout = value; }
    public Duration getScanTimeout() { return scanTimeout; }
    public void setScanTimeout(Duration value) { scanTimeout = value; }
    public int getMaxConcurrentScans() { return maxConcurrentScans; }
    public void setMaxConcurrentScans(int value) { maxConcurrentScans = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { maxAttempts = value; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration value) { retryBackoff = value; }
    public Duration getClaimTimeout() { return claimTimeout; }
    public void setClaimTimeout(Duration value) { claimTimeout = value; }
    public long getMaxStreamBytes() { return maxStreamBytes; }
    public void setMaxStreamBytes(long value) { maxStreamBytes = value; }

    void validate() {
        if (!enabled) return;
        if (host == null || host.isBlank() || port < 1 || port > 65535
                || connectionTimeout == null || connectionTimeout.isNegative() || connectionTimeout.isZero()
                || scanTimeout == null || scanTimeout.isNegative() || scanTimeout.isZero()
                || maxConcurrentScans < 1 || maxConcurrentScans > 20
                || maxAttempts < 1 || maxAttempts > 20
                || retryBackoff == null || retryBackoff.isNegative()
                || claimTimeout == null || claimTimeout.isNegative() || claimTimeout.isZero()
                || maxStreamBytes < 25L * 1024 * 1024 || maxStreamBytes > 100L * 1024 * 1024) {
            throw new IllegalStateException("Attachment malware scanning configuration is invalid.");
        }
    }
}
