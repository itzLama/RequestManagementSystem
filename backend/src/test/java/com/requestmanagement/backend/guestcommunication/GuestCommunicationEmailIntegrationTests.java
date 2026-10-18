package com.requestmanagement.backend.guestcommunication;

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
class GuestCommunicationEmailIntegrationTests {
    private static final String PREFIX = "Step4-";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired GuestCommunicationService communicationService;

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
    void adminGeneralMessageSendsOneEmailAfterCommit() throws Exception {
        long requestId = guestGeneralRequest(title("admin-general"), null);
        Session admin = login("sara.saad@example.com");

        sendAdminGeneral(requestId, admin, "  Admin committed message  ")
                .andExpect(status().isCreated());

        assertOneEmail(requestId, "guest@example.com", "Admin committed message");
        assertThat(communicationCount(requestId)).isEqualTo(1);
    }

    @Test
    void assignedEmployeeGeneralMessageSendsOneEmailAfterCommit() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long requestId = guestGeneralRequest(title("employee-general"), nora);
        Session employee = login("nora.ahmed@example.com");

        sendAssignedGeneral(requestId, employee, "Employee committed message")
                .andExpect(status().isCreated());

        assertOneEmail(requestId, "guest@example.com", "Employee committed message");
    }

    @Test
    void adminActiveProjectMessageSendsOneEmailAfterCommit() throws Exception {
        long projectId = activeProject(title("admin-project"));
        long requestId = guestProjectTask(title("admin-project-task"), projectId);
        Session admin = login("sara.saad@example.com");

        sendAdminProject(projectId, requestId, admin, "Admin project message")
                .andExpect(status().isCreated());

        assertOneEmail(requestId, "project.guest@example.com", "Admin project message");
    }

    @Test
    void activeProjectMemberMessageSendsOneEmailAfterCommit() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = activeProject(title("member-project"));
        addMember(projectId, nora);
        long requestId = guestProjectTask(title("member-project-task"), projectId);
        Session employee = login("nora.ahmed@example.com");

        sendEmployeeProject(projectId, requestId, employee, "Member project message")
                .andExpect(status().isCreated());

        assertOneEmail(requestId, "project.guest@example.com", "Member project message");
    }

    @Test
    void rolledBackCommunicationSendsNoEmailAndPersistsNothing() {
        long requestId = guestGeneralRequest(title("rollback"), null);
        long adminId = userId("sara.saad@example.com");

        transactions.executeWithoutResult(transaction -> {
            communicationService.sendAdminGeneral(
                    requestId, adminId, new SendGuestCommunicationRequest("Rolled back message"));
            transaction.setRollbackOnly();
        });

        assertThat(communicationCount(requestId)).isZero();
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureDoesNotRollbackCommittedCommunicationOrHttpSuccess() throws Exception {
        long requestId = guestGeneralRequest(title("smtp-failure"), null);
        Session admin = login("sara.saad@example.com");
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        sendAdminGeneral(requestId, admin, "Persist despite SMTP failure")
                .andExpect(status().isCreated());

        assertThat(communicationCount(requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT message FROM guest_communications WHERE request_id=?",
                String.class, requestId)).isEqualTo("Persist despite SMTP failure");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void unauthorizedAndInvalidSendsProduceNoEmail() throws Exception {
        long requestId = guestGeneralRequest(title("rejected"), null);
        Session employee = login("nora.ahmed@example.com");
        Session admin = login("sara.saad@example.com");

        sendAdminGeneral(requestId, employee, "Unauthorized")
                .andExpect(status().isForbidden());
        sendAdminGeneral(requestId, admin, "   ")
                .andExpect(status().isBadRequest());

        assertThat(communicationCount(requestId)).isZero();
        verifyNoInteractions(mailSender);
    }

    @Test
    void unrelatedCommentsNotesAssignmentsAndGuestSubmissionProduceNoEmail() throws Exception {
        long requestId = guestGeneralRequest(title("unrelated"), null);
        long nora = userId("nora.ahmed@example.com");
        Session admin = login("sara.saad@example.com");

        authenticatedPost("/api/requests/admin/" + requestId + "/comments",
                admin, "{\"text\":\"Public comment\"}").andExpect(status().isCreated());
        authenticatedPost("/api/requests/admin/" + requestId + "/internal-notes",
                admin, "{\"text\":\"Private note\"}").andExpect(status().isCreated());
        mvc.perform(patch("/api/requests/admin/{id}", requestId)
                        .session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"status\":\"NEW\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().isOk());

        Cookie csrf = csrf();
        mvc.perform(post("/api/guest/requests/general")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.77");
                            return request;
                        })
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("""
                                {"guestName":"No Email Guest","guestEmail":"no-email@example.com",
                                 "title":"%s","description":"Submission only",
                                 "typeId":%d,"priority":"LOW"}
                                """.formatted(title("guest-submission"), activeTypeId())))
                .andExpect(status().isCreated());

        verifyNoInteractions(mailSender);
    }

    private void assertOneEmail(long requestId, String recipient, String message) {
        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage email = captor.getValue();
        assertThat(email.getTo()).containsExactly(recipient);
        assertThat(email.getSubject()).isEqualTo(
                "[Masar] New message for Request #" + requestId);
        assertThat(email.getText()).contains(message);
    }

    private org.springframework.test.web.servlet.ResultActions sendAdminGeneral(
            long requestId, Session session, String message
    ) throws Exception {
        return authenticatedPost("/api/requests/admin/" + requestId + "/guest-communications",
                session, jsonMessage(message));
    }

    private org.springframework.test.web.servlet.ResultActions sendAssignedGeneral(
            long requestId, Session session, String message
    ) throws Exception {
        return authenticatedPost("/api/requests/assigned/" + requestId + "/guest-communications",
                session, jsonMessage(message));
    }

    private org.springframework.test.web.servlet.ResultActions sendAdminProject(
            long projectId, long requestId, Session session, String message
    ) throws Exception {
        return authenticatedPost("/api/projects/" + projectId + "/tasks/" + requestId
                + "/guest-communications", session, jsonMessage(message));
    }

    private org.springframework.test.web.servlet.ResultActions sendEmployeeProject(
            long projectId, long requestId, Session session, String message
    ) throws Exception {
        return authenticatedPost("/api/projects/mine/" + projectId + "/tasks/" + requestId
                + "/guest-communications", session, jsonMessage(message));
    }

    private org.springframework.test.web.servlet.ResultActions authenticatedPost(
            String path, Session session, String content
    ) throws Exception {
        return mvc.perform(post(path).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType("application/json").content(content));
    }

    private String jsonMessage(String message) {
        return "{\"message\":\"" + message + "\"}";
    }

    private long guestGeneralRequest(String title, Long assigneeId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,
                                     guest_name,guest_email,assigned_to)
                VALUES (?,'Step 4 email test',?,'MEDIUM','NEW',
                        'Email Guest','guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, activeTypeId(), assigneeId);
    }

    private long guestProjectTask(String title, long projectId) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,
                                     guest_name,guest_email,project_id)
                VALUES (?,'Step 4 project email test',NULL,?,'HIGH','NEW',
                        'Project Email Guest','project.guest@example.com',?)
                RETURNING request_id
                """, Long.class, title, workTypeId(jdbc, projectId, "Task"), projectId);
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

    private long communicationCount(long requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM guest_communications WHERE request_id=?",
                Long.class, requestId);
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
