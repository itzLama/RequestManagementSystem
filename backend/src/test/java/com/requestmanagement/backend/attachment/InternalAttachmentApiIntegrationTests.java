package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.TestEmployeeFixtureSupport;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectKey;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectMetadata;
import com.requestmanagement.backend.attachmentstorage.AttachmentObjectStorage;
import com.requestmanagement.backend.attachmentstorage.StoredAttachmentObject;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "masar.attachments.upload.enabled=true",
        "masar.attachments.scanning.enabled=true",
        "masar.attachments.scanning.retry-backoff=0s"
})
@AutoConfigureMockMvc
@Transactional
class InternalAttachmentApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskAttachmentRepository attachments;
    @Autowired EntityManager entityManager;
    @MockitoBean AttachmentObjectStorage storage;
    @MockitoBean AttachmentMalwareScanner scanner;

    @Test
    void cleanVerifiedAttachmentIsPromotedAndDownloadedOnlyThroughItsExactTask() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long employeeId = userId("nora.ahmed@example.com");
        membership(project, employeeId);
        long task = task(project, employeeId, null, false);
        byte[] bytes = pdf().getBytes();
        uploadAdmin(admin, project, task).andExpect(status().isAccepted());
        TaskAttachment attachment = latest(task);
        AttachmentObjectMetadata metadata = metadata(bytes, attachment.getSha256Digest());
        when(storage.metadata(any())).thenReturn(metadata);
        when(storage.open(any())).thenAnswer(invocation ->
                new StoredAttachmentObject(new ByteArrayInputStream(bytes), metadata));
        when(scanner.scan(any(), anyLong())).thenAnswer(invocation -> {
            var input = (java.io.InputStream) invocation.getArgument(0);
            byte[] scanned = input.readAllBytes();
            return MalwareScanResult.clean("ClamAV", scanned.length);
        });

        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, task, attachment.getId()).session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.storageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.downloadable").value(true))
                .andExpect(jsonPath("$.objectKey").doesNotExist());

        MvcResult started = mvc.perform(get(
                        "/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, task, attachment.getId()).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes));

        Session employee = login("nora.ahmed@example.com");
        MvcResult employeeDownload = mvc.perform(get(
                        "/api/projects/mine/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, task, attachment.getId()).session(employee.session()))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(employeeDownload)).andExpect(status().isOk());

        TestEmployeeFixtureSupport.EmployeeFixture outsider =
                TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Attachment Non-member");
        Session nonMember = login(outsider.email());
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, task, attachment.getId()).session(nonMember.session()))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        MvcResult archivedDownload = mvc.perform(get(
                        "/api/projects/mine/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, task, attachment.getId()).session(employee.session()))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(archivedDownload)).andExpect(status().isOk());

        long otherTask = task(project, userId("nora.ahmed@example.com"), null, false);
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, otherTask, attachment.getId()).session(admin.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void infectedAndScannerErrorAttachmentsStayUnavailable() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("nora.ahmed@example.com"), null, false);
        byte[] bytes = pdf().getBytes();
        uploadAdmin(admin, project, task).andExpect(status().isAccepted());
        TaskAttachment infected = latest(task);
        AttachmentObjectMetadata metadata = metadata(bytes, infected.getSha256Digest());
        when(storage.metadata(any())).thenReturn(metadata);
        when(storage.open(any())).thenAnswer(invocation ->
                new StoredAttachmentObject(new ByteArrayInputStream(bytes), metadata));
        when(scanner.scan(any(), anyLong())).thenAnswer(invocation -> {
            byte[] scanned = ((java.io.InputStream) invocation.getArgument(0)).readAllBytes();
            return MalwareScanResult.infected("ClamAV", "Eicar-Signature", scanned.length);
        });

        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, task, infected.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadable").value(false));
        assertThat(attachments.findById(infected.getId()).orElseThrow().getScanStatus())
                .isEqualTo(TaskAttachment.ScanStatus.INFECTED);

        uploadAdmin(admin, project, task).andExpect(status().isAccepted());
        TaskAttachment failed = latest(task);
        AttachmentObjectMetadata failedMetadata = metadata(bytes, failed.getSha256Digest());
        when(storage.metadata(any())).thenReturn(failedMetadata);
        when(storage.open(any())).thenAnswer(invocation ->
                new StoredAttachmentObject(new ByteArrayInputStream(bytes), failedMetadata));
        org.mockito.Mockito.doReturn(MalwareScanResult.error("Scanner unavailable.", 0))
                .when(scanner).scan(any(), anyLong());
        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, task, failed.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("ERROR"))
                .andExpect(jsonPath("$.downloadable").value(false));
        verify(storage, never()).promote(any(), any());
    }

    @Test
    void adminUploadsToQuarantineAndReceivesSafePendingMetadata() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("nora.ahmed@example.com"), null, false);
        doNothing().when(storage).uploadQuarantine(any(), any(), any(Long.class), any(), any());

        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.filename").value("report.pdf"))
                .andExpect(jsonPath("$.storageStatus").value("QUARANTINED"))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY"))
                .andExpect(jsonPath("$.downloadable").value(false))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.guestSessionIdSnapshot").doesNotExist());

        TaskAttachment stored = attachments.findAll().stream()
                .filter(attachment -> attachment.getRequestId().equals(task)).findFirst().orElseThrow();
        assertThat(stored.getStorageStatus()).isEqualTo(TaskAttachment.StorageStatus.QUARANTINED);
        assertThat(stored.getScanStatus()).isEqualTo(TaskAttachment.ScanStatus.PENDING);
        assertThat(stored.getSha256Digest()).matches("[0-9a-f]{64}");
        ArgumentCaptor<AttachmentObjectKey> key = ArgumentCaptor.forClass(AttachmentObjectKey.class);
        verify(storage).uploadQuarantine(key.capture(), any(), any(Long.class), any(), any());
        assertThat(key.getValue().isQuarantine()).isTrue();
    }

    @Test
    void memberCanUploadToRootAndSubtaskButNonMemberAndCrossProjectCannot() throws Exception {
        Session employee = login("nora.ahmed@example.com");
        long employeeId = userId("nora.ahmed@example.com");
        long project = project("ACTIVE");
        membership(project, employeeId);
        long parent = task(project, employeeId, null, false);
        long child = task(project, employeeId, parent, false);
        long otherProject = project("ACTIVE");
        long otherTask = task(otherProject, employeeId, null, false);

        uploadEmployee(employee, project, parent).andExpect(status().isAccepted());
        uploadEmployee(employee, project, child).andExpect(status().isAccepted());
        uploadEmployee(employee, otherProject, otherTask).andExpect(status().isNotFound());
        uploadEmployee(employee, project, otherTask).andExpect(status().isNotFound());
    }

    @Test
    void guestCreatedTaskAcceptsInternalUploadButRemainsPrivate() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("sara.saad@example.com"), null, true);

        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.uploadSource").value("INTERNAL"))
                .andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY"))
                .andExpect(jsonPath("$.downloadable").value(false));
    }

    @Test
    void csrfInactiveUserArchivedMutationAndGeneralRequestAreRejectedWhileArchivedListingWorks()
            throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("nora.ahmed@example.com"), null, false);

        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted());

        long general = generalRequest();
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, general)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .session(admin.session()))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());

    }

    @Test
    void deactivatedEmployeesCannotUseAnExistingSession() throws Exception {
        Session employee = login("nora.ahmed@example.com");
        long employeeId = userId("nora.ahmed@example.com");
        long project = project("ACTIVE");
        membership(project, employeeId);
        long task = task(project, employeeId, null, false);
        jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", employeeId);
        entityManager.clear();

        uploadEmployee(employee, project, task).andExpect(status().isUnauthorized());
        verify(storage, never()).uploadQuarantine(any(), any(), any(Long.class), any(), any());
    }

    @Test
    void validationAndStorageFailuresNeverExposePartialContentAndCompensateSafely() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("nora.ahmed@example.com"), null, false);

        MockMultipartFile invalid = new MockMultipartFile("file", "payload.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(invalid).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isBadRequest());
        verify(storage, never()).uploadQuarantine(any(), any(), any(Long.class), any(), any());

        doThrow(new RuntimeException("storage unavailable")).when(storage)
                .uploadQuarantine(any(), any(), any(Long.class), any(), any());
        doNothing().when(storage).delete(any());
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isBadGateway());
        assertThat(latest(task).getStorageStatus()).isEqualTo(TaskAttachment.StorageStatus.UPLOAD_FAILED);

        doThrow(new RuntimeException("storage unavailable")).when(storage)
                .uploadQuarantine(any(), any(), any(Long.class), any(), any());
        doThrow(new RuntimeException("cleanup uncertain")).when(storage).delete(any());
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                        .file(pdf()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isBadGateway());
        assertThat(latest(task).getStorageStatus()).isEqualTo(TaskAttachment.StorageStatus.DELETION_PENDING);
    }

    @Test
    void listingIsExactlyTaskScopedAndGuestRouteRequiresVerification() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long user = userId("nora.ahmed@example.com");
        long first = task(project, user, null, false);
        long second = task(project, user, null, false);
        uploadAdmin(admin, project, first).andExpect(status().isAccepted());
        uploadAdmin(admin, project, second).andExpect(status().isAccepted());

        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments", project, first)
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments/1/content", project, first)
                        .session(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/guest/tracking/attachments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void individualDeletionIsCsrfProtectedScopedIdempotentAndReleasesSlot() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("nora.ahmed@example.com"), null, false);
        uploadAdmin(admin, project, task).andExpect(status().isAccepted());
        TaskAttachment attachment = latest(task);

        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}",
                        project, task, attachment.getId()).session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(attachments.findById(attachment.getId()).orElseThrow().getStorageStatus())
                .isEqualTo(TaskAttachment.StorageStatus.DELETED);
        assertThat(attachments.findReservedSlots(task)).isEmpty();

        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void cleanInternalAttachmentCanBeSharedAndUnsharedOnlyOnGuestRootTask() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long guestTask = task(project, userId("sara.saad@example.com"), null, true);
        uploadAdmin(admin, project, guestTask).andExpect(status().isAccepted());
        TaskAttachment attachment = latest(guestTask);
        makeClean(admin, project, guestTask, attachment);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/guest-visibility",
                        project, guestTask, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestVisible\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("GUEST_VISIBLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_attachment_visibility_events WHERE attachment_id=?",
                Long.class, attachment.getId())).isEqualTo(1L);

        mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/guest-visibility",
                        project, guestTask, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestVisible\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_attachment_visibility_events WHERE attachment_id=?",
                Long.class, attachment.getId())).isEqualTo(2L);
    }

    @Test
    void archivedProjectRejectsDeletionAndVisibilityMutation() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long task = task(project, userId("sara.saad@example.com"), null, true);
        uploadAdmin(admin, project, task).andExpect(status().isAccepted());
        TaskAttachment attachment = latest(task);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/guest-visibility",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestVisible\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void permanentParentDeletionCleansParentAndSubtaskAttachmentsBeforeDatabaseRows() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("ACTIVE");
        long user = userId("nora.ahmed@example.com");
        long parent = task(project, user, null, false);
        long child = task(project, user, parent, false);
        uploadAdmin(admin, project, parent).andExpect(status().isAccepted());
        uploadAdmin(admin, project, child).andExpect(status().isAccepted());

        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}", project, parent)
                        .param("deleteSubtasks", "true").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?)",
                Long.class, parent, child)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_attachments WHERE request_id IN (?,?)",
                Long.class, parent, child)).isZero();
    }

    private void makeClean(Session admin, long project, long task, TaskAttachment attachment) throws Exception {
        byte[] bytes = pdf().getBytes();
        AttachmentObjectMetadata metadata = metadata(bytes, attachment.getSha256Digest());
        when(storage.metadata(any())).thenReturn(metadata);
        when(storage.open(any())).thenAnswer(invocation ->
                new StoredAttachmentObject(new ByteArrayInputStream(bytes), metadata));
        when(scanner.scan(any(), anyLong())).thenAnswer(invocation -> {
            byte[] scanned = ((java.io.InputStream) invocation.getArgument(0)).readAllBytes();
            return MalwareScanResult.clean("ClamAV", scanned.length);
        });
        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, task, attachment.getId()).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions uploadAdmin(Session session, long project, long task)
            throws Exception {
        return mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, task)
                .file(pdf()).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    private org.springframework.test.web.servlet.ResultActions uploadEmployee(Session session, long project, long task)
            throws Exception {
        return mvc.perform(multipart("/api/projects/mine/{projectId}/tasks/{taskId}/attachments", project, task)
                .file(pdf()).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf",
                "%PDF-1.7\ntrusted\n%%EOF".getBytes(StandardCharsets.US_ASCII));
    }

    private AttachmentObjectMetadata metadata(byte[] bytes, String sha256) {
        return new AttachmentObjectMetadata(bytes.length, "application/pdf", "test-etag",
                Map.of("sha256", sha256));
    }

    private TaskAttachment latest(long task) {
        return attachments.findAll().stream().filter(item -> item.getRequestId().equals(task))
                .max(java.util.Comparator.comparing(TaskAttachment::getId)).orElseThrow();
    }

    private long project(String status) {
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",
                Long.class, "Attachment API " + UUID.randomUUID(), status);
        jdbc.update("INSERT INTO project_work_types(project_id,work_type_name) VALUES (?,'Task')", project);
        return project;
    }

    private void membership(long project, long employee) {
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
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
                    VALUES ('Guest file','Details',?,'MEDIUM','NEW',?,'Guest','guest@example.com',?,?)
                    RETURNING request_id
                    """, Long.class, workType, status, project, parent);
        }
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,
                                     created_by,project_id,assigned_to,parent_request_id)
                VALUES ('Internal file','Details',?,'MEDIUM','NEW',?,?,?,?,?) RETURNING request_id
                """, Long.class, workType, status, user, project, user, parent);
    }

    private long generalRequest() {
        long type = jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1", Long.class);
        long user = userId("nora.ahmed@example.com");
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by)
                VALUES ('General file','Details',?,'MEDIUM','NEW',?) RETURNING request_id
                """, Long.class, type, user);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Cookie csrf() throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private Session login(String email) throws Exception {
        Cookie csrf = csrf();
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
