package com.requestmanagement.poc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Explicitly invoked, development-only S3 compatibility probe. This class is
 * isolated from Masar's backend build and has no production startup hook.
 */
public final class RustFsStoragePoc {
    private static final String ENABLE_FLAG = "RUSTFS_POC_ENABLED";
    private static final String PERSISTENCE_KEY = "poc/persistence/restart-probe.txt";
    private static final byte[] PERSISTENCE_BYTES =
            "Masar RustFS restart persistence probe".getBytes(StandardCharsets.UTF_8);
    private static final Set<String> MODES =
            Set.of("round-trip", "put-persistence", "verify-persistence", "unauthorized");

    private RustFsStoragePoc() {
    }

    public static void main(String[] args) {
        requireEnabled();
        String mode = args.length == 0 ? "round-trip" : args[0];
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("Mode must be one of: " + MODES);
        }

        Config config = Config.fromEnvironment();
        try (S3Client client = client(config, config.accessKey(), config.secretKey())) {
            switch (mode) {
                case "round-trip" -> roundTrip(client, config);
                case "put-persistence" -> putPersistenceProbe(client, config);
                case "verify-persistence" -> verifyPersistenceProbe(client, config);
                case "unauthorized" -> verifyUnauthorizedAccess(config);
                default -> throw new IllegalStateException("Unexpected mode: " + mode);
            }
        }
    }

    private static void roundTrip(S3Client client, Config config) {
        ensurePrivateBucket(client, config.bucket());
        verifyUnauthorizedAccess(config);

        String key = "poc/round-trip/" + UUID.randomUUID() + "/probe.txt";
        byte[] original = "Masar RustFS S3 compatibility probe".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(original);

        client.putObject(
                PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(key)
                        .contentType("text/plain; charset=utf-8")
                        .metadata(Map.of("masar-sha256", checksum, "poc", "true"))
                        .build(),
                RequestBody.fromBytes(original));

        HeadObjectResponse metadata = client.headObject(
                HeadObjectRequest.builder().bucket(config.bucket()).key(key).build());
        require(metadata.contentLength() == original.length, "Unexpected content length");
        require(checksum.equals(metadata.metadata().get("masar-sha256")), "Metadata checksum mismatch");

        ResponseBytes<GetObjectResponse> downloaded = client.getObject(
                GetObjectRequest.builder().bucket(config.bucket()).key(key).build(),
                ResponseTransformer.toBytes());
        require(Arrays.equals(original, downloaded.asByteArray()), "Downloaded bytes differ from upload");

        client.deleteObject(DeleteObjectRequest.builder().bucket(config.bucket()).key(key).build());
        requireObjectMissing(client, config.bucket(), key);
        System.out.println("POC PASS: authenticated upload, metadata, download, byte verification and deletion succeeded.");
    }

    private static void putPersistenceProbe(S3Client client, Config config) {
        ensurePrivateBucket(client, config.bucket());
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(PERSISTENCE_KEY)
                        .contentType("text/plain; charset=utf-8")
                        .metadata(Map.of("masar-sha256", sha256(PERSISTENCE_BYTES), "poc", "restart"))
                        .build(),
                RequestBody.fromBytes(PERSISTENCE_BYTES));
        System.out.println("POC PASS: persistence marker stored. Restart the container, then run verify-persistence.");
    }

    private static void verifyPersistenceProbe(S3Client client, Config config) {
        ResponseBytes<GetObjectResponse> downloaded = client.getObject(
                GetObjectRequest.builder().bucket(config.bucket()).key(PERSISTENCE_KEY).build(),
                ResponseTransformer.toBytes());
        require(Arrays.equals(PERSISTENCE_BYTES, downloaded.asByteArray()),
                "Persistence marker was missing or changed after restart");
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.bucket()).key(PERSISTENCE_KEY).build());
        requireObjectMissing(client, config.bucket(), PERSISTENCE_KEY);
        System.out.println("POC PASS: object persisted across restart and was cleaned up.");
    }

    private static void verifyUnauthorizedAccess(Config config) {
        try (S3Client unauthorized = client(config, "POCUNAUTHORIZEDKEY", "poc-unauthorized-secret-value")) {
            try {
                unauthorized.headBucket(HeadBucketRequest.builder().bucket(config.bucket()).build());
                throw new IllegalStateException("Security check failed: invalid credentials accessed the private bucket");
            } catch (S3Exception exception) {
                require(exception.statusCode() == 401 || exception.statusCode() == 403,
                        "Expected 401/403 for invalid credentials, received " + exception.statusCode());
            }
        }
        System.out.println("POC PASS: invalid credentials were rejected.");
    }

    private static void ensurePrivateBucket(S3Client client, String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        }
    }

    private static void requireObjectMissing(S3Client client, String bucket, String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            throw new IllegalStateException("Deleted object remains accessible: " + key);
        } catch (NoSuchKeyException exception) {
            // Expected S3 response.
        } catch (S3Exception exception) {
            require(exception.statusCode() == 404,
                    "Expected 404 after deletion, received " + exception.statusCode());
        }
    }

    private static S3Client client(Config config, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(config.endpoint())
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireEnabled() {
        if (!"true".equalsIgnoreCase(System.getenv(ENABLE_FLAG))) {
            throw new IllegalStateException(
                    "This isolated POC is disabled. Set " + ENABLE_FLAG + "=true explicitly to run it.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Config(
            URI endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey) {

        private static Config fromEnvironment() {
            URI endpoint = URI.create(required("RUSTFS_POC_ENDPOINT"));
            if (!"http".equalsIgnoreCase(endpoint.getScheme())
                    || !Set.of("127.0.0.1", "localhost").contains(endpoint.getHost())) {
                throw new IllegalArgumentException("POC endpoint must use HTTP on localhost");
            }

            String bucket = required("RUSTFS_POC_BUCKET");
            if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
                throw new IllegalArgumentException("POC bucket must be a valid lowercase S3 bucket name");
            }

            return new Config(
                    endpoint,
                    required("RUSTFS_POC_REGION"),
                    bucket,
                    required("RUSTFS_POC_ACCESS_KEY"),
                    required("RUSTFS_POC_SECRET_KEY"));
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank() || value.startsWith("REPLACE_WITH_")) {
                throw new IllegalArgumentException(name + " must be set to a non-placeholder value");
            }
            return value.trim();
        }
    }
}
