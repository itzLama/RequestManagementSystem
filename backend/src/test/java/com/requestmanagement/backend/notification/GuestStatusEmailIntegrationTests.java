package com.requestmanagement.backend.notification;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.request.RequestStatus;
import com.requestmanagement.backend.request.RequestStatusService;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GuestStatusEmailIntegrationTests {
    private static final String PREFIX = "Step5-";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired RequestStatusService requestStatusService;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void resetMailSender() {
        reset(mailSender);
    }

    @AfterEach
    void removeCommittedFixtures() {
        jdbc.update("""
                DELETE FROM guest_communications WHERE request_id IN
                (SELECT request_id FROM requests WHERE title LIKE ?)
                """, PREFIX + "%");
        jdbc.update("""
                DELETE FROM comments WHERE request_id IN
                (SELECT request_id FROM requests WHERE title LIKE ?)
                """, PREFIX + "%");
        jdbc.update("""
                DELETE FROM status_history WHERE request_id IN
                (SELECT request_id FROM requests WHERE title LIKE ?)
                """, PREFIX + "%");
        jdbc.update("DELETE FROM requests WHERE title LIKE ?", PREFIX + "%");
        jdbc.update("""
                DELETE FROM project_memberships WHERE project_id IN
                (SELECT project_id FROM projects WHERE project_name LIKE ?)
                """, PREFIX + "%");
        jdbc.update("""
                DELETE FROM project_work_types WHERE project_id IN
                (SELECT project_id FROM projects WHERE project_name LIKE ?)
                """, PREFIX + "%");
        jdbc.update("""
                DELETE FROM project_status_columns WHERE project_id IN
                (SELECT project_id FROM projects WHERE project_name LIKE ?)
                """, PREFIX + "%");
        jdbc.update("DELETE FROM projects WHERE project_name LIKE ?", PREFIX + "%");
    }

    @Test
    void adminGeneralGuestStatusChangeSendsExactlyOneEmailAfterCommit() throws Exception {
        long requestId = guestGeneralRequest(title("admin-general"), "NEW", null);

        updateAdminGeneral(requestId, login("sara.saad@example.com"),
                "IN_PROGRESS", null).andExpect(status().isOk());

        assertOneStatusEmail(requestId, "IN_PROGRESS");
        assertPersistedTransition(requestId, "IN_PROGRESS", 1);
    }

    @Test
    void assignedEmployeeGeneralGuestStatusChangeSendsExactlyOneEmailAfterCommit() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long requestId = guestGeneralRequest(title("employee-general"), "NEW", nora);

        updateAssignedGeneral(requestId, login("nora.ahmed@example.com"), "WAITING_USER")
                .andExpect(status().isOk());

        assertOneStatusEmail(requestId, "WAITING_USER");
        assertPersistedTransition(requestId, "WAITING_USER", 1);
    }

    @Test
    void adminProjectGuestStatusChangeSendsExactlyOneEmailAfterCommit() throws Exception {
        long projectId = activeProject(title("admin-project"));
        long taskId = guestProjectTask(title("admin-project-task"), projectId, "NEW");

        updateAdminProject(projectId, taskId, login("sara.saad@example.com"), "IN_PROGRESS")
                .andExpect(status().isOk());

        assertOneStatusEmail(taskId, "In Progress");
        assertPersistedTransition(taskId, "IN_PROGRESS", 1);
    }

    @Test
    void employeeProjectMemberGuestStatusChangeSendsExactlyOneEmailAfterCommit() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = activeProject(title("employee-project"));
        addMember(projectId, nora);
        long taskId = guestProjectTask(title("employee-project-task"), projectId, "NEW");

        updateEmployeeProject(projectId, taskId, login("nora.ahmed@example.com"), "COMPLETED")
                .andExpect(status().isOk());

        assertOneStatusEmail(taskId, "Completed");
        assertPersistedTransition(taskId, "COMPLETED", 1);
    }

    @Test
    void sameStatusAssignmentOnlyAndPriorityOnlyChangesSendNoEmail() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long sameStatusId = guestGeneralRequest(title("same-status"), "NEW", null);
        long assignmentId = guestGeneralRequest(title("assignment-only"), "NEW", null);
        long priorityId = guestGeneralRequest(title("priority-only"), "NEW", null);
        Session admin = login("sara.saad@example.com");

        updateAdminGeneral(sameStatusId, admin, "NEW", null).andExpect(status().isOk());
        updateAdminGeneral(assignmentId, admin, "NEW", nora).andExpect(status().isOk());
        jdbc.update("UPDATE requests SET priority='HIGH' WHERE request_id=?", priorityId);

        verifyNoInteractions(mailSender);
        assertThat(historyCount(sameStatusId)).isZero();
        assertThat(historyCount(assignmentId)).isZero();
        assertThat(historyCount(priorityId)).isZero();
    }

    @Test
    void authenticatedUserRequestStatusChangeSendsNoGuestEmail() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long requestId = employeeGeneralRequest(title("authenticated"), nora, nora);

        updateAssignedGeneral(requestId, login("nora.ahmed@example.com"), "IN_PROGRESS")
                .andExpect(status().isOk());

        assertPersistedTransition(requestId, "IN_PROGRESS", 1);
        verifyNoInteractions(mailSender);
    }

    @Test
    void commentsNotesAndGuestSubmissionSendNoStatusEmail() throws Exception {
        long requestId = guestGeneralRequest(title("unrelated"), "NEW", null);
        Session admin = login("sara.saad@example.com");

        authenticatedPost("/api/requests/admin/" + requestId + "/comments", admin,
                "{\"text\":\"Public comment\"}").andExpect(status().isCreated());
        authenticatedPost("/api/requests/admin/" + requestId + "/internal-notes", admin,
                "{\"text\":\"Private note\"}").andExpect(status().isCreated());

        Cookie csrf = csrf();
        mvc.perform(post("/api/guest/requests/general")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.88");
                            return request;
                        })
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content(String.format(Locale.ROOT, """
                                {"guestName":"Status Guest","guestEmail":"status@example.com",
                                 "title":"%s","description":"Submission only",
                                 "typeId":%d,"priority":"LOW"}
                                """, title("submission"), activeTypeId())))
                .andExpect(status().isCreated());

        verifyNoInteractions(mailSender);
    }

    @Test
    void unauthorizedAndInvalidStatusUpdatesSendNoEmail() throws Exception {
        long requestId = guestGeneralRequest(title("rejected"), "NEW", null);

        updateAdminGeneral(requestId, login("nora.ahmed@example.com"),
                "IN_PROGRESS", null).andExpect(status().isForbidden());
        Session admin = login("sara.saad@example.com");
        mvc.perform(patch("/api/requests/admin/{id}", requestId)
                        .session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"status\":\"NOT_A_STATUS\",\"assignedToId\":null}"))
                .andExpect(status().isBadRequest());

        assertThat(currentStatus(requestId)).isEqualTo("NEW");
        assertThat(historyCount(requestId)).isZero();
        verifyNoInteractions(mailSender);
    }

    @Test
    void rolledBackStatusChangeSendsNoEmailAndPersistsNoTransition() {
        long requestId = guestGeneralRequest(title("rollback"), "NEW", null);
        long adminId = userId("sara.saad@example.com");

        transactions.executeWithoutResult(transaction -> {
            Request request = requestRepository.findById(requestId).orElseThrow();
            User admin = userRepository.findById(adminId).orElseThrow();
            requestStatusService.changeStatus(
                    request, RequestStatus.IN_PROGRESS, admin, "Rolled back transition");
            transaction.setRollbackOnly();
        });

        assertThat(currentStatus(requestId)).isEqualTo("NEW");
        assertThat(historyCount(requestId)).isZero();
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailurePreservesCommittedStatusHistoryAndSuccessfulResponse() throws Exception {
        long requestId = guestGeneralRequest(title("smtp-failure"), "NEW", null);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        updateAdminGeneral(requestId, login("sara.saad@example.com"),
                "IN_PROGRESS", null).andExpect(status().isOk());

        assertPersistedTransition(requestId, "IN_PROGRESS", 1);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void reopeningCompletedAndRejectedGuestRequestsSendsOneEmailPerRealTransition() throws Exception {
        Session admin = login("sara.saad@example.com");
        for (String terminalStatus : new String[] {"COMPLETED", "REJECTED"}) {
            reset(mailSender);
            long requestId = guestGeneralRequest(
                    title("reopen-" + terminalStatus.toLowerCase()), terminalStatus, null);

            updateAdminGeneral(requestId, admin, "IN_PROGRESS", null)
                    .andExpect(status().isOk());

            assertOneStatusEmail(requestId, "IN_PROGRESS");
            assertPersistedTransition(requestId, "IN_PROGRESS", 1);
        }
    }

    private void assertOneStatusEmail(long requestId, String statusValue) {
        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage email = captor.getValue();
        assertThat(email.getTo()).containsExactly("guest@example.com");
        assertThat(email.getSubject()).isEqualTo(
                "[Masar] Request #" + requestId + " status updated");
        assertThat(email.getText()).contains(statusValue)
                .doesNotContain("localhost", "http://", "https://", "/track-request");
    }

    private void assertPersistedTransition(long requestId, String statusValue, long historyCount) {
        assertThat(currentStatus(requestId)).isEqualTo(statusValue);
        assertThat(historyCount(requestId)).isEqualTo(historyCount);
        assertThat(jdbc.queryForObject("""
                SELECT new_status FROM status_history
                WHERE request_id=? ORDER BY history_id DESC LIMIT 1
                """, String.class, requestId)).isEqualTo(statusValue);
    }

    private org.springframework.test.web.servlet.ResultActions updateAdminGeneral(
            long requestId, Session session, String statusValue, Long assigneeId
    ) throws Exception {
        String assignee = assigneeId == null ? "null" : assigneeId.toString();
        return mvc.perform(patch("/api/requests/admin/{id}", requestId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json")
                .content("{\"status\":\"" + statusValue
                        + "\",\"assignedToId\":" + assignee + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions updateAssignedGeneral(
            long requestId, Session session, String statusValue
    ) throws Exception {
        return mvc.perform(patch("/api/requests/assigned/{id}/status", requestId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json")
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions updateAdminProject(
            long projectId, long taskId, Session session, String statusValue
    ) throws Exception {
        return mvc.perform(patch("/api/projects/{projectId}/tasks/{taskId}/status", projectId, taskId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json")
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions updateEmployeeProject(
            long projectId, long taskId, Session session, String statusValue
    ) throws Exception {
        return mvc.perform(patch(
                        "/api/projects/mine/{projectId}/tasks/{taskId}/status", projectId, taskId)
                .session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json")
                .content("{\"status\":\"" + statusValue + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions authenticatedPost(
            String path, Session session, String content
    ) throws Exception {
        return mvc.perform(post(path).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(content));
    }

    private long guestGeneralRequest(String title, String statusValue, Long assigneeId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,
                                     guest_name,guest_email,assigned_to)
                VALUES (?,'Step 5 status email test',?,'MEDIUM',?,
                        'Status Guest','guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, activeTypeId(), statusValue, assigneeId);
    }

    private long employeeGeneralRequest(String title, long creatorId, long assigneeId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by,assigned_to)
                VALUES (?,'Authenticated request',?,'MEDIUM','NEW',?,?) RETURNING request_id
                """, Long.class, title, activeTypeId(), creatorId, assigneeId);
    }

    private long guestProjectTask(String title, long projectId, String statusValue) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,
                                     guest_name,guest_email,project_id)
                VALUES (?,'Step 5 project status email test',NULL,?,'HIGH',?,
                        'Project Status Guest','guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, workTypeId(jdbc, projectId, "Task"), statusValue, projectId);
    }

    private long activeProject(String name) {
        return jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, name);
    }

    private void addMember(long projectId, long employeeId) {
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)",
                projectId, employeeId);
    }

    private long historyCount(long requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, requestId);
    }

    private String currentStatus(long requestId) {
        return jdbc.queryForObject(
                "SELECT status FROM requests WHERE request_id=?", String.class, requestId);
    }

    private long activeTypeId() {
        return jdbc.queryForObject(
                "SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1",
                Long.class);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private String title(String suffix) {
        return PREFIX + suffix + "-" + UUID.randomUUID();
    }

    private Cookie csrf() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private Session login(String email) throws Exception {
        Cookie csrf = csrf();
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
