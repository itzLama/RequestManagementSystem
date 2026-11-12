package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.project.ProjectAccessService;
import com.requestmanagement.backend.project.ProjectRepository;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentDeletionServiceTests {
    @Test
    void ambiguousStorageFailureRecordsRetryableStateAndNeverFinalizesDeletion() {
        @SuppressWarnings("unchecked") ObjectProvider<AttachmentObjectStorage> provider = mock(ObjectProvider.class);
        AttachmentObjectStorage storage = mock(AttachmentObjectStorage.class);
        AttachmentLifecycleService lifecycle = mock(AttachmentLifecycleService.class);
        TaskAttachmentRepository attachments = mock(TaskAttachmentRepository.class);
        TaskAttachmentVisibilityEventRepository events = mock(TaskAttachmentVisibilityEventRepository.class);
        AttachmentProcessingProperties properties = new AttachmentProcessingProperties();
        TaskAttachment attachment = mock(TaskAttachment.class);
        when(provider.getIfAvailable()).thenReturn(storage);
        when(lifecycle.claimCleanup(eq(4L), any())).thenReturn(attachment);
        when(attachment.getObjectKey()).thenReturn("quarantine/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        doThrow(new RuntimeException("unavailable")).when(storage).delete(any());
        AttachmentDeletionService service = new AttachmentDeletionService(provider, lifecycle, attachments, events,
                mock(ProjectRepository.class), mock(RequestRepository.class), mock(UserRepository.class),
                mock(ProjectAccessService.class), properties, mock(AttachmentMetadataPurgeService.class));

        assertThatThrownBy(() -> service.cleanup(4L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(lifecycle).recordCleanupFailure(eq(4L), any(), anyString(), any());
        verify(lifecycle, never()).finalizeClaimedDeletion(anyLong(), any());
    }
}
