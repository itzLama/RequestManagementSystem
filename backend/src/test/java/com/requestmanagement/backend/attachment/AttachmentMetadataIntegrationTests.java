package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeyGenerator;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKeys;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestPermanentDeletionService;
import com.requestmanagement.backend.request.RequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AttachmentMetadataIntegrationTests {
    @Autowired AttachmentLifecycleService lifecycle;
    @Autowired AttachmentObjectKeyGenerator keyGenerator;
    @Autowired TaskAttachmentRepository attachments;
    @Autowired TaskAttachmentVisibilityEventRepository visibilityEvents;
    @Autowired RequestRepository requests;
    @Autowired RequestPermanentDeletionService deletionService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationRejectsGeneralRequestsAndCrossProjectOwnership() {
        long user = userId("nora.ahmed@example.com");
        long general = generalRequest(user);
        long firstProject = project();
        long secondProject = project();
        long task = task(firstProject, user, null, false);

        assertThatThrownBy(() -> rawAttachment(firstProject, general, user, 1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> rawAttachment(secondProject, task, user, 1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rootTasksAndSubtasksHaveIndependentTenSlotCapacity() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long parent = task(project, user, null, false);
        long child = task(project, user, parent, false);

        for (int index = 0; index < 10; index++) {
            assertThat(reserveInternal(project, parent, user).getSlotNumber()).isEqualTo((short) (index + 1));
            assertThat(reserveInternal(project, child, user).getSlotNumber()).isEqualTo((short) (index + 1));
        }

        assertThatThrownBy(() -> reserveInternal(project, parent, user))
                .isInstanceOfSatisfying(AttachmentLifecycleException.class,
                        error -> assertThat(error.getReason())
                                .isEqualTo(AttachmentLifecycleException.Reason.CAPACITY_EXCEEDED));
        assertThatThrownBy(() -> reserveInternal(project, child, user))
                .isInstanceOf(AttachmentLifecycleException.class);
    }

    @Test
    void slotsReleaseOnlyAfterSafeTerminalStates() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long task = task(project, user, null, false);

        TaskAttachment failed = reserveInternal(project, task, user);
        lifecycle.recordUploadFailure(project, task, failed.getId());
        assertThat(reserveInternal(project, task, user).getSlotNumber()).isEqualTo((short) 1);

        TaskAttachment pendingDeletion = reserveInternal(project, task, user);
        lifecycle.markDeletionPending(project, task, pendingDeletion.getId());
        assertThat(reserveInternal(project, task, user).getSlotNumber()).isEqualTo((short) 3);
        lifecycle.finalizeDeletion(project, task, pendingDeletion.getId());
        assertThat(reserveInternal(project, task, user).getSlotNumber()).isEqualTo((short) 2);
    }

    @Test
    void lifecycleRequiresCleanScanAndCompleteMetadataBeforeAvailability() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long task = task(project, user, null, false);
        AttachmentObjectKeys keys = keyGenerator.generate();
        TaskAttachment attachment = lifecycle.reserveInternal(project, task, user, "report.pdf",
                "application/pdf", keys.quarantine());

        assertThat(attachment.isDownloadable()).isFalse();
        assertThatThrownBy(() -> lifecycle.markAvailable(project, task, attachment.getId(), keys.available()))
                .isInstanceOf(AttachmentLifecycleException.class);

        lifecycle.markQuarantined(project, task, attachment.getId(), "application/pdf", 512, "a".repeat(64));
        lifecycle.recordScanOutcome(project, task, attachment.getId(), TaskAttachment.ScanStatus.ERROR,
                "scanner", "Unavailable");
        assertThatThrownBy(() -> lifecycle.markAvailable(project, task, attachment.getId(), keys.available()))
                .isInstanceOf(AttachmentLifecycleException.class);
        assertThat(attachment.isDownloadable()).isFalse();
    }

    @Test
    void cleanAttachmentCanBecomeAvailableAndTerminalDeletionIsIdempotent() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long task = task(project, user, null, false);
        AttachmentObjectKeys keys = keyGenerator.generate();
        TaskAttachment attachment = lifecycle.reserveInternal(project, task, user, "report.pdf", null,
                keys.quarantine());
        lifecycle.markQuarantined(project, task, attachment.getId(), "application/pdf", 512, "b".repeat(64));
        lifecycle.recordScanOutcome(project, task, attachment.getId(), TaskAttachment.ScanStatus.CLEAN,
                "scanner", null);
        lifecycle.markAvailable(project, task, attachment.getId(), keys.available());

        assertThat(attachment.isDownloadable()).isTrue();
        lifecycle.markDeletionPending(project, task, attachment.getId());
        assertThat(attachment.isDownloadable()).isFalse();
        lifecycle.finalizeDeletion(project, task, attachment.getId());
        lifecycle.finalizeDeletion(project, task, attachment.getId());
        assertThat(attachment.getStorageStatus()).isEqualTo(TaskAttachment.StorageStatus.DELETED);
    }

    @Test
    void scanClaimsAreAtomicAndBoundedByAttempts() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long task = task(project, user, null, false);
        TaskAttachment attachment = reserveInternal(project, task, user);
        lifecycle.markQuarantined(project, task, attachment.getId(), "application/pdf", 1, "d".repeat(64));
        UUID first = UUID.randomUUID();

        TaskAttachment claimed = lifecycle.claimForScan(project, task, attachment.getId(), first,
                1, Duration.ofMinutes(10));
        assertThat(claimed.getScanClaimToken()).isEqualTo(first);
        assertThat(claimed.getScanAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> lifecycle.claimForScan(project, task, attachment.getId(), UUID.randomUUID(),
                1, Duration.ofMinutes(10))).isInstanceOfSatisfying(AttachmentLifecycleException.class,
                error -> assertThat(error.getReason())
                        .isEqualTo(AttachmentLifecycleException.Reason.INVALID_TRANSITION));

        lifecycle.releaseScanClaim(project, task, attachment.getId(), first);
        assertThatThrownBy(() -> lifecycle.claimForScan(project, task, attachment.getId(), UUID.randomUUID(),
                1, Duration.ofMinutes(10))).isInstanceOf(AttachmentLifecycleException.class);
    }

    @Test
    void provenanceVisibilityAndAuditRemainRequestScopedAndSecretFree() {
        long actor = userId("sara.saad@example.com");
        long project = project();
        long guestTask = task(project, actor, null, true);
        UUID nonSecretSessionId = UUID.randomUUID();
        AttachmentObjectKeys guestKeys = keyGenerator.generate();
        TaskAttachment guest = lifecycle.reserveGuest(project, guestTask, nonSecretSessionId,
                "guest.pdf", "application/pdf", guestKeys.quarantine());

        assertThat(guest.getUploadSource()).isEqualTo(TaskAttachment.UploadSource.GUEST);
        assertThat(guest.getVisibility()).isEqualTo(TaskAttachment.Visibility.GUEST_VISIBLE);
        assertThat(guest.getGuestSessionIdSnapshot()).isEqualTo(nonSecretSessionId);
        assertThat(guest.getUploadedBy()).isNull();
        assertThat(guest.isDownloadable()).isFalse();
        long subtask = task(project, actor, guestTask, false);
        assertThatThrownBy(() -> lifecycle.reserveGuest(project, subtask, UUID.randomUUID(),
                "hidden.pdf", null, keyGenerator.generate().quarantine()))
                .isInstanceOfSatisfying(AttachmentLifecycleException.class,
                        error -> assertThat(error.getReason())
                                .isEqualTo(AttachmentLifecycleException.Reason.INVALID_OWNER));

        List<String> provenanceColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema='public' AND table_name='task_attachments'
                   AND (column_name LIKE '%token%' OR column_name LIKE '%otp%'
                        OR column_name LIKE '%digest%' OR column_name LIKE '%secret%')
                """, String.class);
        assertThat(provenanceColumns).containsExactlyInAnyOrder(
                "scan_claim_token", "cleanup_claim_token", "sha256_digest");

        AttachmentObjectKeys internalKeys = keyGenerator.generate();
        TaskAttachment internal = lifecycle.reserveInternal(project, guestTask, actor, "internal.pdf", null,
                internalKeys.quarantine());
        assertThat(internal.getVisibility()).isEqualTo(TaskAttachment.Visibility.INTERNAL_ONLY);
        lifecycle.markQuarantined(project, guestTask, internal.getId(), "application/pdf", 1, "c".repeat(64));
        lifecycle.recordScanOutcome(project, guestTask, internal.getId(), TaskAttachment.ScanStatus.CLEAN,
                "scanner", null);
        lifecycle.markAvailable(project, guestTask, internal.getId(), internalKeys.available());
        lifecycle.changeGuestVisibility(project, guestTask, internal.getId(), actor,
                TaskAttachment.Visibility.GUEST_VISIBLE);

        List<TaskAttachmentVisibilityEvent> audit =
                visibilityEvents.findByAttachment_IdOrderByOccurredAtAscIdAsc(internal.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().getAction()).isEqualTo(TaskAttachmentVisibilityEvent.Action.SHARED);
        assertThat(audit.getFirst().getRequestId()).isEqualTo(guestTask);
    }

    @Test
    void metadataBlocksTaskAndParentDeletionWithoutAStorageCleanupSaga() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long parent = task(project, user, null, false);
        long child = task(project, user, parent, false);
        reserveInternal(project, child, user);
        Request parentEntity = requests.findById(parent).orElseThrow();
        Request childEntity = requests.findById(child).orElseThrow();

        assertThatThrownBy(() -> deletionService.delete(childEntity))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("attachments must be cleaned");
        assertThatThrownBy(() -> deletionService.deleteParentWithSubtasks(parentEntity, List.of(childEntity)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("attachments must be cleaned");
        assertThat(requests.existsById(parent)).isTrue();
        assertThat(requests.existsById(child)).isTrue();
    }

    @Test
    void taskWithoutAttachmentKeepsExistingPermanentDeletionBehavior() {
        long user = userId("nora.ahmed@example.com");
        long project = project();
        long task = task(project, user, null, false);
        deletionService.delete(requests.findById(task).orElseThrow());
        assertThat(requests.existsById(task)).isFalse();
    }

    private TaskAttachment reserveInternal(long project, long task, long user) {
        return lifecycle.reserveInternal(project, task, user, "file.pdf", "application/pdf",
                keyGenerator.generate().quarantine());
    }

    private long project() {
        long project = jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Attachment metadata " + UUID.randomUUID());
        jdbc.update("INSERT INTO project_work_types(project_id,work_type_name) VALUES (?,'Task')", project);
        return project;
    }

    private long task(long project, long user, Long parent, boolean guest) {
        long workType = jdbc.queryForObject(
                "SELECT work_type_id FROM project_work_types WHERE project_id=? ORDER BY work_type_id LIMIT 1",
                Long.class, project);
        long status = jdbc.queryForObject(
                "SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'",
                Long.class, project);
        if (guest) {
            return jdbc.queryForObject("""
                    INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,
                                         guest_name,guest_email,project_id,parent_request_id)
                    VALUES (?,'Details',?,'MEDIUM','NEW',?,'Guest','guest@example.com',?,?) RETURNING request_id
                    """, Long.class, "Guest attachment task", workType, status, project, parent);
        }
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,
                                     created_by,project_id,assigned_to,parent_request_id)
                VALUES (?,'Details',?,'MEDIUM','NEW',?,?,?,?,?) RETURNING request_id
                """, Long.class, "Attachment task", workType, status, user, project, user, parent);
    }

    private long generalRequest(long user) {
        long type = jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1", Long.class);
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by)
                VALUES ('Attachment general','Details',?,'MEDIUM','NEW',?) RETURNING request_id
                """, Long.class, type, user);
    }

    private void rawAttachment(long project, long request, long user, int slot) {
        jdbc.update("""
                INSERT INTO task_attachments(project_id,request_id,slot_number,original_filename,object_key,
                    upload_source,uploaded_by_user_id,visibility,scan_status,storage_status)
                VALUES (?,?,?,'raw.pdf',?,'INTERNAL',?,'INTERNAL_ONLY','PENDING','PENDING')
                """, project, request, slot, keyGenerator.generate().quarantine().value(), user);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }
}
