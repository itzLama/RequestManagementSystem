package com.requestmanagement.backend.attachmentstorage;

import java.io.InputStream;
import java.util.Map;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import java.util.List;

public final class S3AttachmentObjectStorage implements AttachmentObjectStorage {
    private final S3Client client;
    private final String bucket;

    S3AttachmentObjectStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void uploadQuarantine(
            AttachmentObjectKey key,
            InputStream content,
            long contentLength,
            String contentType,
            Map<String, String> metadata
    ) {
        if (!key.isQuarantine()) throw new IllegalArgumentException("Upload requires a quarantine object key.");
        if (contentLength < 0) throw new IllegalArgumentException("Content length must not be negative.");
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key.value())
                            .contentLength(contentLength)
                            .contentType(contentType)
                            .metadata(metadata == null ? Map.of() : Map.copyOf(metadata))
                            .build(),
                    RequestBody.fromInputStream(content, contentLength));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public StoredAttachmentObject open(AttachmentObjectKey key) {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(key.value()).build());
            return new StoredAttachmentObject(response, metadata(response.response()));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public AttachmentObjectMetadata metadata(AttachmentObjectKey key) {
        try {
            return metadata(client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key.value()).build()));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void promote(AttachmentObjectKey quarantineKey, AttachmentObjectKey availableKey) {
        if (!quarantineKey.isQuarantine() || !availableKey.isAvailable()) {
            throw new IllegalArgumentException("Promotion requires quarantine and available object keys.");
        }
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .copySource(bucket + "/" + quarantineKey.value())
                    .destinationBucket(bucket)
                    .destinationKey(availableKey.value())
                    .build());
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void delete(AttachmentObjectKey key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key.value()).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw translate(exception);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean exists(AttachmentObjectKey key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key.value()).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw translate(exception);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<AttachmentStoredObjectSummary> listOwned(String namespace, int limit) {
        if (!("quarantine".equals(namespace) || "available".equals(namespace)) || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("A bounded owned attachment namespace is required.");
        }
        try {
            return client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket)
                            .prefix(namespace + "/").maxKeys(limit).build()).contents().stream()
                    .map(item -> new AttachmentStoredObjectSummary(new AttachmentObjectKey(item.key()),
                            item.size(), item.lastModified())).toList();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public AttachmentStorageDiagnostic diagnostic() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return AttachmentStorageDiagnostic.healthy();
        } catch (RuntimeException exception) {
            return AttachmentStorageDiagnostic.unavailable();
        }
    }

    private AttachmentObjectMetadata metadata(HeadObjectResponse response) {
        return new AttachmentObjectMetadata(response.contentLength(), response.contentType(),
                response.eTag(), response.metadata());
    }

    private AttachmentObjectMetadata metadata(GetObjectResponse response) {
        return new AttachmentObjectMetadata(response.contentLength(), response.contentType(),
                response.eTag(), response.metadata());
    }

    private AttachmentStorageException translate(RuntimeException exception) {
        if (exception instanceof S3Exception s3Exception) {
            int status = s3Exception.statusCode();
            if (status == 404) return new AttachmentObjectNotFoundException(exception);
            if (status == 401 || status == 403) {
                return new AttachmentStorageException(AttachmentStorageException.Failure.UNAUTHORIZED,
                        "Attachment storage rejected the configured credentials.", exception);
            }
            if (status == 408 || status == 429 || status >= 500) {
                return new AttachmentStorageException(AttachmentStorageException.Failure.UNAVAILABLE,
                        "Attachment storage is temporarily unavailable.", exception);
            }
        }
        if (exception instanceof SdkClientException) {
            return new AttachmentStorageException(AttachmentStorageException.Failure.UNAVAILABLE,
                    "Attachment storage is temporarily unavailable.", exception);
        }
        return new AttachmentStorageException(AttachmentStorageException.Failure.UNEXPECTED,
                "The attachment storage operation failed.", exception);
    }
}
