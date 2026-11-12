package com.requestmanagement.backend.attachment;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.requestmanagement.backend.guesttracking.GuestTrackingService;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "masar.attachments.upload.enabled=true",
        "masar.attachments.scanning.enabled=true",
        "masar.attachments.processing.automatic-scanning-enabled=true",
        "masar.attachments.processing.guest-upload-enabled=true",
        "masar.attachments.processing.interval=1h",
        "masar.attachments.scanning.retry-backoff=0s"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "REAL_ATTACHMENT_UPLOAD_INTEGRATION", matches = "true")
class RealRustFsAttachmentUploadIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired S3Client s3;
    @Autowired AttachmentUploadProperties uploadProperties;
    @Autowired AttachmentMalwareScanner scanner;
    @Autowired AutomaticAttachmentScanWorker automaticScanWorker;
    @MockitoBean JavaMailSender mailSender;

    @BeforeAll
    static void createDedicatedBucket(@Autowired S3Client s3,
                                      @Autowired com.requestmanagement.backend.attachmentstorage.AttachmentStorageProperties properties) {
        s3.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
    }

    @AfterAll
    static void removeOnlyOwnedObjectsAndBucket(
            @Autowired S3Client s3,
            @Autowired com.requestmanagement.backend.attachmentstorage.AttachmentStorageProperties properties) {
        var objects = s3.listObjectsV2(request -> request.bucket(properties.getBucket())).contents();
        for (var object : objects) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(object.key()).build());
        }
        assertThat(s3.listObjectsV2(request -> request.bucket(properties.getBucket())).contents()).isEmpty();
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(properties.getBucket()).build());
    }

    @Test
    void realRustFsAndClamAvSecureLifecycleWorksEndToEnd() throws Exception {
        assertThat(scanner.ready()).as("real ClamAV readiness").isTrue();
        Session admin = login("sara.saad@example.com");
        Session member = login("nora.ahmed@example.com");
        String nonMemberEmail = "real-upload-outsider-" + UUID.randomUUID() + "@example.com";
        jdbc.update("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES ('Real Upload Outsider',?,
                        (SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',true)
                """, nonMemberEmail);
        Session nonMember = login(nonMemberEmail);
        long memberId = userId("nora.ahmed@example.com");
        long project = project();
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, memberId);
        long parent = task(project, memberId, null);
        long subtask = task(project, memberId, parent);

        byte[] cleanBytes = pdfBytes(false);
        MvcResult adminUpload = mvc.perform(multipart(
                        "/api/projects/{projectId}/tasks/{taskId}/attachments", project, parent)
                        .file(pdf("admin.pdf", cleanBytes)).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.storageStatus").value("QUARANTINED"))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY"))
                .andExpect(jsonPath("$.downloadable").value(false))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andReturn();

        long attachmentId = ((Number) com.jayway.jsonpath.JsonPath.read(
                adminUpload.getResponse().getContentAsString(), "$.id")).longValue();
        String objectKey = jdbc.queryForObject(
                "SELECT object_key FROM task_attachments WHERE attachment_id=?", String.class, attachmentId);
        assertThat(objectKey).startsWith("quarantine/");
        var object = s3.headObject(HeadObjectRequest.builder()
                .bucket(System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET")).key(objectKey).build());
        assertThat(object.contentLength()).isPositive();
        assertThat(object.metadata()).containsKey("sha256");
        assertThat(object.metadata().get("sha256")).isEqualTo(sha256(cleanBytes));

        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, parent, attachmentId).session(admin.session()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, parent, attachmentId).session(admin.session()))
                .andExpect(status().isForbidden());

        automaticScanWorker.processBatch();

        String availableKey = jdbc.queryForObject(
                "SELECT object_key FROM task_attachments WHERE attachment_id=?", String.class, attachmentId);
        assertThat(availableKey).startsWith("available/");
        assertThat(s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET")).key(availableKey).build()).asByteArray())
                .containsExactly(cleanBytes);
        assertThat(s3.headObject(HeadObjectRequest.builder()
                .bucket(System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET")).key(availableKey).build())
                .metadata().get("sha256")).isEqualTo(sha256(cleanBytes));
        assertThat(jdbc.queryForMap("""
                SELECT scan_status,storage_status,scan_claim_token FROM task_attachments
                 WHERE attachment_id=?
                """, attachmentId))
                .containsEntry("scan_status", "CLEAN")
                .containsEntry("storage_status", "AVAILABLE")
                .containsEntry("scan_claim_token", null);

        MvcResult download = mvc.perform(get(
                        "/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, parent, attachmentId).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        mvc.perform(asyncDispatch(download)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(cleanBytes));
        MvcResult memberDownload = mvc.perform(get(
                        "/api/projects/mine/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, parent, attachmentId).session(member.session()))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(memberDownload)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(cleanBytes));

        String listing = mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments", project, parent)
                        .session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(listing).doesNotContain(objectKey, System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET"),
                System.getenv("MASAR_ATTACHMENTS_STORAGE_ENDPOINT"));

        MvcResult employeeUpload = mvc.perform(multipart(
                        "/api/projects/mine/{projectId}/tasks/{taskId}/attachments", project, subtask)
                        .file(pdf("employee.pdf", cleanBytes)).session(member.session()).cookie(member.csrf())
                        .header("X-XSRF-TOKEN", member.csrf().getValue()))
                .andExpect(status().isAccepted()).andReturn();
        long employeeAttachment = ((Number) com.jayway.jsonpath.JsonPath.read(
                employeeUpload.getResponse().getContentAsString(), "$.id")).longValue();
        String employeeKey = jdbc.queryForObject(
                "SELECT object_key FROM task_attachments WHERE attachment_id=?", String.class, employeeAttachment);
        assertThat(s3.headObject(HeadObjectRequest.builder()
                .bucket(System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET")).key(employeeKey).build())
                .contentLength()).isPositive();

        mvc.perform(multipart("/api/projects/mine/{projectId}/tasks/{taskId}/attachments", project, parent)
                        .file(pdf("denied.pdf", cleanBytes)).session(nonMember.session()).cookie(nonMember.csrf())
                        .header("X-XSRF-TOKEN", nonMember.csrf().getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, parent)
                        .file(pdf("csrf.pdf", cleanBytes)).session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/guest/tracking/attachments")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, parent, attachmentId).session(nonMember.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, subtask, attachmentId).session(admin.session()))
                .andExpect(status().isNotFound());

        byte[] infectedBytes = infectedDocxBytes();
        MvcResult infectedUpload = mvc.perform(multipart(
                        "/api/projects/{projectId}/tasks/{taskId}/attachments", project, parent)
                        .file(new MockMultipartFile("file", "eicar-test.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                infectedBytes)).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted()).andReturn();
        long infectedId = ((Number) com.jayway.jsonpath.JsonPath.read(
                infectedUpload.getResponse().getContentAsString(), "$.id")).longValue();
        String infectedQuarantineKey = jdbc.queryForObject(
                "SELECT object_key FROM task_attachments WHERE attachment_id=?", String.class, infectedId);
        mvc.perform(post("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/scan",
                        project, parent, infectedId).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("INFECTED"))
                .andExpect(jsonPath("$.downloadable").value(false));
        assertThat(jdbc.queryForObject(
                "SELECT scan_status FROM task_attachments WHERE attachment_id=?", String.class, infectedId))
                .isEqualTo("INFECTED");
        assertThat(s3.listObjectsV2(request -> request.bucket(
                        System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET"))).contents())
                .noneMatch(item -> item.key().equals(infectedQuarantineKey)
                        || item.key().equals("available/" + infectedQuarantineKey.substring("quarantine/".length())));
        mvc.perform(get("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}/content",
                        project, parent, infectedId).session(admin.session()))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM task_attachments
                 WHERE attachment_id=? AND (scan_status <> 'PENDING' OR storage_status <> 'QUARANTINED')
                """, Long.class, employeeAttachment)).isZero();

        mvc.perform(delete("/api/projects/{projectId}/tasks/{taskId}/attachments/{attachmentId}",
                        project, parent, attachmentId).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT storage_status FROM task_attachments WHERE attachment_id=?",
                String.class, attachmentId)).isEqualTo("DELETED");
        assertThat(s3.listObjectsV2(request -> request.bucket(
                        System.getenv("MASAR_ATTACHMENTS_STORAGE_BUCKET"))).contents())
                .noneMatch(item -> item.key().endsWith(availableKey.substring("available/".length())));

        uploadProperties.setEnabled(false);
        try {
            mvc.perform(multipart("/api/projects/{projectId}/tasks/{taskId}/attachments", project, parent)
                            .file(pdf("disabled.pdf", cleanBytes)).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            uploadProperties.setEnabled(true);
        }
    }

    @Test
    void realGuestUploadAndExplicitInternalSharingRemainRequestScoped() throws Exception {
        assertThat(scanner.ready()).as("real ClamAV readiness").isTrue();
        Session admin = login("sara.saad@example.com");
        long project = project();
        long guestTask = guestTask(project, "real-guest-" + UUID.randomUUID() + "@example.com");
        GuestSession guest = verifiedGuestSession(guestTask,
                jdbc.queryForObject("SELECT guest_email FROM requests WHERE request_id=?", String.class, guestTask));
        byte[] bytes = pdfBytes(false);

        MvcResult guestUpload = mvc.perform(multipart("/api/guest/tracking/attachments")
                        .file(pdf("guest.pdf", bytes)).cookie(guest.sessionCookie(), guest.csrf())
                        .header("X-XSRF-TOKEN", guest.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.visibility").value("GUEST_VISIBLE"))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andReturn();
        long guestAttachment = ((Number) com.jayway.jsonpath.JsonPath.read(
                guestUpload.getResponse().getContentAsString(), "$.id")).longValue();
        automaticScanWorker.processBatch();
        mvc.perform(get("/api/guest/tracking/attachments").cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(guestAttachment))
                .andExpect(jsonPath("$[0].downloadable").value(true));
        MvcResult guestDownload = mvc.perform(get("/api/guest/tracking/attachments/{id}/content",
                        guestAttachment).cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(guestDownload)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).containsExactly(bytes));

        MvcResult internalUpload = mvc.perform(multipart(
                        "/api/projects/{projectId}/tasks/{taskId}/attachments", project, guestTask)
                        .file(pdf("team.pdf", bytes)).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY")).andReturn();
        long internalAttachment = ((Number) com.jayway.jsonpath.JsonPath.read(
                internalUpload.getResponse().getContentAsString(), "$.id")).longValue();
        automaticScanWorker.processBatch();
        mvc.perform(get("/api/guest/tracking/attachments").cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/attachments/{id}/guest-visibility",
                        project, guestTask, internalAttachment).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestVisible\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("GUEST_VISIBLE"));
        mvc.perform(get("/api/guest/tracking/attachments").cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        MvcResult sharedDownload = mvc.perform(get("/api/guest/tracking/attachments/{id}/content",
                        internalAttachment).cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(asyncDispatch(sharedDownload)).andExpect(status().isOk());

        mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/attachments/{id}/guest-visibility",
                        project, guestTask, internalAttachment).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestVisible\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("INTERNAL_ONLY"));
        mvc.perform(get("/api/guest/tracking/attachments/{id}/content", internalAttachment)
                        .cookie(guest.sessionCookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/guest/tracking/attachments").cookie(guest.sessionCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    private MockMultipartFile pdf(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "application/pdf", bytes);
    }

    private byte[] pdfBytes(boolean infected) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("%PDF-1.7\nreal RustFS integration\n".getBytes(StandardCharsets.US_ASCII));
        if (infected) {
            String encoded = "WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=";
            bytes.write(Base64.getDecoder().decode(encoded));
            bytes.write('\n');
        }
        bytes.write("%%EOF".getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    private byte[] infectedDocxBytes() throws Exception {
        String encoded = "WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<Types><Override ContentType=\"application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document\"/></Types>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/eicar-test.txt"));
            zip.write(Base64.getDecoder().decode(encoded));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long project() {
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Real RustFS upload " + UUID.randomUUID());
        jdbc.update("INSERT INTO project_work_types(project_id,work_type_name) VALUES (?,'Task')", project);
        return project;
    }

    private long task(long project, long user, Long parent) {
        long workType = jdbc.queryForObject(
                "SELECT work_type_id FROM project_work_types WHERE project_id=? LIMIT 1", Long.class, project);
        long status = jdbc.queryForObject(
                "SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'",
                Long.class, project);
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,
                                     created_by,project_id,assigned_to,parent_request_id)
                VALUES ('Real upload','Details',?,'MEDIUM','NEW',?,?,?,?,?) RETURNING request_id
                """, Long.class, workType, status, user, project, user, parent);
    }

    private long guestTask(long project, String email) {
        long workType = jdbc.queryForObject(
                "SELECT work_type_id FROM project_work_types WHERE project_id=? LIMIT 1", Long.class, project);
        long status = jdbc.queryForObject(
                "SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'",
                Long.class, project);
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,
                                     guest_name,guest_email,project_id)
                VALUES ('Real Guest upload','Guest details',?,'MEDIUM',?,'Real Guest',?,?) RETURNING request_id
                """, Long.class, workType, status, email, project);
    }

    private GuestSession verifiedGuestSession(long requestId, String email) throws Exception {
        Cookie csrf = csrf();
        mvc.perform(post("/api/guest/tracking/request-code").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"" + requestId + "\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher matcher = Pattern.compile("code is (\\d{6})").matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        String challenge = jdbc.queryForObject("""
                SELECT challenge_id::text FROM guest_tracking_challenges
                 WHERE request_id=? ORDER BY created_at DESC LIMIT 1
                """, String.class, requestId);
        MvcResult verified = mvc.perform(post("/api/guest/tracking/verify").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challenge + "\",\"code\":\"" + matcher.group(1) + "\"}"))
                .andExpect(status().isNoContent()).andReturn();
        return new GuestSession(verified.getResponse().getCookie(GuestTrackingService.COOKIE), csrf);
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
    private record GuestSession(Cookie sessionCookie, Cookie csrf) { }
}
