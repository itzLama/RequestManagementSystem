package com.requestmanagement.backend.attachmentstorage;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("masar.attachments.storage")
public class AttachmentStorageProperties {
    private boolean enabled;
    private URI endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyle = true;
    private boolean allowInsecureHttp;
    private Duration connectionTimeout = Duration.ofSeconds(2);
    private Duration apiCallAttemptTimeout = Duration.ofSeconds(5);
    private Duration apiCallTimeout = Duration.ofSeconds(10);
    private int maxConnections = 25;

    public void validateForUse() {
        if (!enabled) return;
        require(endpoint != null, "Attachment storage endpoint is required.");
        require(hasText(region), "Attachment storage region is required.");
        require(hasText(bucket) && bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"),
                "Attachment storage bucket is invalid.");
        require(isCredentialConfigured(accessKey), "Attachment storage access key is required.");
        require(isCredentialConfigured(secretKey), "Attachment storage secret key is required.");
        require(endpoint.isAbsolute() && endpoint.getHost() != null,
                "Attachment storage endpoint must be an absolute URI.");
        require(endpoint.getUserInfo() == null && endpoint.getQuery() == null && endpoint.getFragment() == null,
                "Attachment storage endpoint must not contain credentials, a query, or a fragment.");
        require(endpoint.getPath() == null || endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()),
                "Attachment storage endpoint must not contain a path.");

        String scheme = endpoint.getScheme().toLowerCase();
        if ("http".equals(scheme)) {
            require(allowInsecureHttp && Set.of("127.0.0.1", "localhost", "::1").contains(endpoint.getHost()),
                    "Insecure attachment storage is allowed only for an explicitly enabled loopback endpoint.");
        } else {
            require("https".equals(scheme), "Attachment storage endpoint must use HTTPS.");
        }

        requirePositive(connectionTimeout, "connection timeout");
        requirePositive(apiCallAttemptTimeout, "API call attempt timeout");
        requirePositive(apiCallTimeout, "API call timeout");
        require(!apiCallAttemptTimeout.minus(apiCallTimeout).isPositive(),
                "Attachment storage API call attempt timeout must not exceed the total timeout.");
        require(maxConnections > 0 && maxConnections <= 500,
                "Attachment storage maximum connections must be between 1 and 500.");
    }

    private static void requirePositive(Duration value, String label) {
        require(value != null && !value.isZero() && !value.isNegative(),
                "Attachment storage " + label + " must be positive.");
    }

    private static boolean isCredentialConfigured(String value) {
        return hasText(value) && !value.toLowerCase().startsWith("replace_with_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public boolean isPathStyle() { return pathStyle; }
    public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public Duration getApiCallAttemptTimeout() { return apiCallAttemptTimeout; }
    public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) { this.apiCallAttemptTimeout = apiCallAttemptTimeout; }
    public Duration getApiCallTimeout() { return apiCallTimeout; }
    public void setApiCallTimeout(Duration apiCallTimeout) { this.apiCallTimeout = apiCallTimeout; }
    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    @Override
    public String toString() {
        return "AttachmentStorageProperties[enabled=" + enabled
                + ", endpoint=" + endpoint
                + ", region=" + region
                + ", bucket=" + bucket
                + ", accessKey=<redacted>, secretKey=<redacted>"
                + ", pathStyle=" + pathStyle
                + ", allowInsecureHttp=" + allowInsecureHttp
                + ", connectionTimeout=" + connectionTimeout
                + ", apiCallAttemptTimeout=" + apiCallAttemptTimeout
                + ", apiCallTimeout=" + apiCallTimeout
                + ", maxConnections=" + maxConnections + "]";
    }
}
