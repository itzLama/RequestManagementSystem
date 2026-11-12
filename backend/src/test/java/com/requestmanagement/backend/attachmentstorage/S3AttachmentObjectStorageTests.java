package com.requestmanagement.backend.attachmentstorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3AttachmentObjectStorageTests {
    private S3Client client;
    private S3AttachmentObjectStorage storage;
    private AttachmentObjectKeys keys;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3AttachmentObjectStorage(client, "private-test-bucket");
        keys = new AttachmentObjectKeyGenerator().generate();
    }

    @Test
    void uploadsStreamToQuarantineWithKnownLengthAndMetadata() {
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.uploadQuarantine(keys.quarantine(), new ByteArrayInputStream(new byte[] {1, 2, 3}),
                3, "application/pdf", Map.of("sha256", "abc"));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("private-test-bucket");
        assertThat(request.getValue().key()).isEqualTo(keys.quarantine().value());
        assertThat(request.getValue().contentLength()).isEqualTo(3);
        assertThat(request.getValue().metadata()).containsEntry("sha256", "abc");
    }

    @Test
    void readsMetadataAndStreamsDownloadWithCloseableResource() throws IOException {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength(3L).contentType("image/png").eTag("etag")
                .metadata(Map.of("sha256", "abc")).build();
        TrackingInputStream input = new TrackingInputStream(new byte[] {1, 2, 3});
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(response,
                AbortableInputStream.create(input));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        try (StoredAttachmentObject stored = storage.open(keys.available())) {
            assertThat(stored.stream().readAllBytes()).containsExactly(1, 2, 3);
            assertThat(stored.metadata().contentLength()).isEqualTo(3);
            assertThat(stored.metadata().customMetadata()).containsEntry("sha256", "abc");
        }

        assertThat(input.closed).isTrue();
    }

    @Test
    void promotesByServerSideCopyAndDeletesIdempotently() {
        storage.promote(keys.quarantine(), keys.available());
        storage.delete(keys.quarantine());

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(client).copyObject(copy.capture());
        assertThat(copy.getValue().copySource())
                .isEqualTo("private-test-bucket/" + keys.quarantine().value());
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deletionTreatsMissingObjectAsAlreadyDeleted() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());

        storage.delete(keys.available());
    }

    @Test
    void mapsMissingObjectWithoutLeakingObjectKey() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());

        assertThatThrownBy(() -> storage.metadata(keys.available()))
                .isInstanceOf(AttachmentObjectNotFoundException.class)
                .hasMessage("The stored attachment was not found.")
                .hasMessageNotContaining(keys.available().value());
    }

    @Test
    void mapsAuthenticationAndUnavailableFailures() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());
        assertThatThrownBy(() -> storage.exists(keys.available()))
                .isInstanceOfSatisfying(AttachmentStorageException.class,
                        error -> assertThat(error.failure()).isEqualTo(AttachmentStorageException.Failure.UNAUTHORIZED));

        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection failed"));
        assertThatThrownBy(() -> storage.exists(keys.available()))
                .isInstanceOfSatisfying(AttachmentStorageException.class,
                        error -> assertThat(error.failure()).isEqualTo(AttachmentStorageException.Failure.UNAVAILABLE));
    }

    @Test
    void diagnosticsAreBoundedByClientConfigurationAndRevealNoDetails() {
        when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        assertThat(storage.diagnostic()).isEqualTo(AttachmentStorageDiagnostic.healthy());

        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.create("endpoint detail"));
        assertThat(storage.diagnostic()).isEqualTo(AttachmentStorageDiagnostic.unavailable());
    }

    @Test
    void headMetadataIsMappedWithoutAwsTypes() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(9L).contentType("application/pdf").eTag("tag")
                .metadata(Map.of("sha256", "digest")).build());

        AttachmentObjectMetadata metadata = storage.metadata(keys.available());
        assertThat(metadata.contentLength()).isEqualTo(9);
        assertThat(metadata.contentType()).isEqualTo("application/pdf");
        assertThat(metadata.customMetadata()).containsEntry("sha256", "digest");
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
