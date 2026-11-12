package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentBackgroundWorkersTests {
    @Test
    void automaticWorkerIsDisabledByDefaultAndProcessesOnlyBoundedCandidatesWhenEnabled() {
        AttachmentProcessingProperties processing = new AttachmentProcessingProperties();
        AttachmentScanningProperties scanning = new AttachmentScanningProperties();
        TaskAttachmentRepository repository = mock(TaskAttachmentRepository.class);
        AttachmentScanService scans = mock(AttachmentScanService.class);
        AutomaticAttachmentScanWorker worker = new AutomaticAttachmentScanWorker(processing, scanning, repository, scans);
        worker.processBatch();
        verifyNoInteractions(repository, scans);

        processing.setAutomaticScanningEnabled(true);
        scanning.setEnabled(true);
        TaskAttachment attachment = mock(TaskAttachment.class);
        when(attachment.getProjectId()).thenReturn(2L);
        when(attachment.getRequestId()).thenReturn(3L);
        when(repository.findScanCandidates(any(LocalDateTime.class), any(LocalDateTime.class), eq(3), eq(10)))
                .thenReturn(List.of(7L));
        when(repository.findMetadata(7L)).thenReturn(Optional.of(attachment));
        worker.processBatch();
        verify(scans).processEligible(2L, 3L, 7L);
    }

    @Test
    void reconciliationIsNonDestructiveUntilBothExplicitGatesAreEnabled() {
        AttachmentProcessingProperties processing = new AttachmentProcessingProperties();
        TaskAttachmentRepository repository = mock(TaskAttachmentRepository.class);
        AttachmentLifecycleService lifecycle = mock(AttachmentLifecycleService.class);
        AttachmentDeletionService deletion = mock(AttachmentDeletionService.class);
        @SuppressWarnings("unchecked") ObjectProvider<AttachmentObjectStorage> provider = mock(ObjectProvider.class);
        AttachmentReconciliationService reconciliation =
                new AttachmentReconciliationService(processing, repository, lifecycle, deletion, provider);

        processing.setReconciliationEnabled(true);
        reconciliation.reconcile();
        verifyNoInteractions(repository, lifecycle, deletion, provider);

        processing.setDestructiveReconciliationEnabled(true);
        processing.setCleanupClaimTimeout(Duration.ofMinutes(10));
        when(repository.findCleanupCandidates(any(), any(), anyInt(), anyInt())).thenReturn(List.of(9L));
        when(repository.findStaleUploadCandidates(any(), anyInt())).thenReturn(List.of());
        reconciliation.reconcile();
        verify(deletion).cleanup(9L);
    }

    @Test
    void guestRateLimiterIsSessionScopedAndBounded() {
        GuestAttachmentRateLimiter limiter = new GuestAttachmentRateLimiter();
        java.util.UUID first = java.util.UUID.randomUUID();
        limiter.acquire(first, 2);
        limiter.acquire(first, 2);
        assertThatThrownBy(() -> limiter.acquire(first, 2))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        limiter.acquire(java.util.UUID.randomUUID(), 2);
    }
}
