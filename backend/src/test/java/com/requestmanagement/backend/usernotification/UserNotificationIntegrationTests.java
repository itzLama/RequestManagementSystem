package com.requestmanagement.backend.usernotification;

import com.requestmanagement.backend.TestEmployeeFixtureSupport;
import com.requestmanagement.backend.guesttracking.GuestTrackingCrypto;
import com.requestmanagement.backend.guesttracking.GuestTrackingTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional on purpose: notifications are written only after the business transaction
 * commits, so these tests commit real data to the masar_test database and clean it up afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserNotificationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean UserNotificationService notificationService;
    @Autowired GuestTrackingCrypto guestCrypto;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Long> projects = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private long nora;
    private TestEmployeeFixtureSupport.EmployeeFixture peer;
    private long project;
    private long workType;

    @BeforeEach
    void setUp() {
        nora = jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class);
        peer = TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Notification Peer");
        users.add(peer.id());
        project = jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Notifications " + System.nanoTime());
        projects.add(project);
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?),(?,?)", project, nora, project, peer.id());
        workType = workTypeId(jdbc, project, "Task");
    }

    @AfterEach
    void cleanUp() {
        reset(notificationService);
        for (Long id : projects) {
            jdbc.update("DELETE FROM user_notifications WHERE project_id=?", id);
            jdbc.update("DELETE FROM guest_communications WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM guest_tracking_sessions WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM comments WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM status_history WHERE request_id IN (SELECT request_id FROM requests WHERE project_id=?)", id);
            jdbc.update("DELETE FROM requests WHERE project_id=? AND parent_request_id IS NOT NULL", id);
            jdbc.update("DELETE FROM requests WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_memberships WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_work_types WHERE project_id=?", id);
            jdbc.update("DELETE FROM project_status_columns WHERE project_id=?", id);
            jdbc.update("DELETE FROM projects WHERE project_id=?", id);
        }
        for (Long id : users) jdbc.update("DELETE FROM users WHERE user_id=?", id);
        projects.clear();
        users.clear();
    }

    @Test
    void assigneeIsNotifiedAboutOthersActionsButNeverAboutTheirOwn() throws Exception {
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        Session peerSession = login(peer.email());

        long task = adminCreateTask(admin, nora);
        expectNotifications(noraSession, 1);
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].taskId").value(task))
                .andExpect(jsonPath("$.items[0].projectId").value(project))
                .andExpect(jsonPath("$.items[0].actorName").value("Sara Saad"))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // Nora's own actions on her own task never notify her.
        mvc.perform(write(patch("/api/projects/mine/" + project + "/tasks/" + task + "/status"), noraSession)
                .content("{\"statusId\":" + statusId("IN_PROGRESS") + "}")).andExpect(status().is2xxSuccessful());
        mvc.perform(write(post("/api/projects/mine/" + project + "/tasks/" + task + "/comments"), noraSession)
                .content("{\"text\":\"My own note\"}")).andExpect(status().is2xxSuccessful());
        expectNotifications(noraSession, 1);

        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/status"), admin)
                .content("{\"statusId\":" + statusId("COMPLETED") + "}")).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("TASK_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].statusName").value("Completed"));

        mvc.perform(write(post("/api/projects/" + project + "/tasks/" + task + "/comments"), admin)
                .content("{\"text\":\"Admin comment\"}")).andExpect(status().is2xxSuccessful());
        mvc.perform(write(post("/api/projects/" + project + "/tasks/" + task + "/internal-notes"), admin)
                .content("{\"text\":\"Internal note\"}")).andExpect(status().is2xxSuccessful());
        mvc.perform(write(post("/api/projects/mine/" + project + "/tasks/" + task + "/comments"), peerSession)
                .content("{\"text\":\"Peer comment\"}")).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].type").value("TASK_COMMENTED"))
                .andExpect(jsonPath("$.items[0].actorName").value("Notification Peer"))
                .andExpect(jsonPath("$.items[1].type").value("TASK_COMMENTED"))
                .andExpect(jsonPath("$.items[1].actorName").value("Sara Saad"))
                .andExpect(jsonPath("$.unreadCount").value(4));

        // Creating a task assigned to yourself is not a notification.
        mvc.perform(write(post("/api/projects/mine/" + project + "/tasks"), noraSession)
                .content(taskBody(nora))).andExpect(status().is2xxSuccessful());
        expectNotifications(noraSession, 4);

        // Reassigning notifies only the new assignee.
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/assignee"), admin)
                .content("{\"assignedToId\":" + peer.id() + "}")).andExpect(status().is2xxSuccessful());
        expectNotifications(noraSession, 4);
        mvc.perform(get("/api/notifications").session(peerSession.session()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE actor_id = recipient_id", Long.class)).isZero();
    }

    @Test
    void usersCanOnlyReadAndMarkTheirOwnNotifications() throws Exception {
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        Session peerSession = login(peer.email());
        adminCreateTask(admin, nora);
        adminCreateTask(admin, nora);
        long first = notificationIds(nora).get(0); long second = notificationIds(nora).get(1);

        mvc.perform(get("/api/notifications").session(peerSession.session()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(write(post("/api/notifications/" + first + "/read"), peerSession)).andExpect(status().isNotFound());
        mvc.perform(write(post("/api/notifications/read-all"), peerSession)).andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE recipient_id=? AND read_at IS NULL",
                Long.class, nora)).isGreaterThanOrEqualTo(2);

        mvc.perform(get("/api/notifications/unread-count").session(noraSession.session()))
                .andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(write(post("/api/notifications/" + first + "/read"), noraSession))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(get("/api/notifications?unreadOnly=true").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id").value(second));
        mvc.perform(write(post("/api/notifications/read-all"), noraSession))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(write(post("/api/notifications/999999999/read"), noraSession)).andExpect(status().isNotFound());

        // Pagination.
        mvc.perform(get("/api/notifications?page=0&size=1").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/notifications?page=1&size=1").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.hasMore").value(false));

        // No anonymous (or Guest) access to any notification endpoint.
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificationsForProjectsTheUserLeftAreHidden() throws Exception {
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        adminCreateTask(admin, nora);
        long id = notificationIds(nora).get(0);
        expectNotifications(noraSession, 1);

        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, nora);
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(write(post("/api/notifications/" + id + "/read"), noraSession)).andExpect(status().isNotFound());
    }

    @Test
    void creatorAndAllParticipantsAreNotifiedExactlyOncePerComment() throws Exception {
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        Session peerSession = login(peer.email());
        long sara = userId("sara.saad@example.com");
        // Peer creates the task and assigns it to Nora.
        String created = mvc.perform(write(post("/api/projects/mine/" + project + "/tasks"), peerSession).content(taskBody(nora)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        long task = json.readTree(created).get("id").asLong();

        // Status change by the Admin: assignee and creator are notified, the Admin is not.
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/status"), admin)
                .content("{\"statusId\":" + statusId("IN_PROGRESS") + "}")).andExpect(status().is2xxSuccessful());
        assertThat(count(nora, task, "TASK_STATUS_CHANGED")).isEqualTo(1);
        assertThat(count(peer.id(), task, "TASK_STATUS_CHANGED")).isEqualTo(1);
        assertThat(count(sara, task, "TASK_STATUS_CHANGED")).isZero();

        comment(noraSession, "/api/projects/mine/", task);   // creator (peer) notified; Sara is not a participant yet
        assertThat(count(peer.id(), task, "TASK_COMMENTED")).isEqualTo(1);
        assertThat(count(sara, task, "TASK_COMMENTED")).isZero();

        comment(admin, "/api/projects/", task);               // Nora is assignee AND commenter: still one notification
        assertThat(count(nora, task, "TASK_COMMENTED")).isEqualTo(1);
        assertThat(count(peer.id(), task, "TASK_COMMENTED")).isEqualTo(2);

        comment(noraSession, "/api/projects/mine/", task);   // previous commenter (Sara, an Admin) is now included
        assertThat(count(sara, task, "TASK_COMMENTED")).isEqualTo(1);
        assertThat(count(peer.id(), task, "TASK_COMMENTED")).isEqualTo(3);
        assertThat(count(nora, task, "TASK_COMMENTED")).isEqualTo(1);

        mvc.perform(get("/api/notifications").session(admin.session()))
                .andExpect(jsonPath("$.items[0].type").value("TASK_COMMENTED"))
                .andExpect(jsonPath("$.items[0].actorName").value("Nora Ahmed"))
                .andExpect(jsonPath("$.items[0].taskId").value(task));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE actor_id = recipient_id", Long.class)).isZero();
    }

    @Test
    void internalNotesOnlyEverReachAdminParticipants() throws Exception {
        long secondAdmin = createUser("Second Admin", "ADMIN", true);
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        Session adminTwo = login(email(secondAdmin));
        long sara = userId("sara.saad@example.com");
        long task = adminCreateTask(admin, nora);
        comment(adminTwo, "/api/projects/", task);
        comment(noraSession, "/api/projects/mine/", task);

        note(admin, task);       // participants: Sara (actor), Nora (assignee, commenter), Second Admin (commenter)
        assertThat(count(secondAdmin, task, "TASK_INTERNAL_NOTE")).isEqualTo(1);
        note(adminTwo, task);    // Sara is the creator and an Admin
        assertThat(count(sara, task, "TASK_INTERNAL_NOTE")).isEqualTo(1);
        assertThat(count(nora, task, "TASK_INTERNAL_NOTE")).isZero();
        mvc.perform(get("/api/notifications?size=50").session(noraSession.session()))
                .andExpect(jsonPath("$.items[?(@.type == 'TASK_INTERNAL_NOTE')]").isEmpty());

        // Defence in depth: even a direct internal-note event for an Employee is never stored.
        assertThat(notificationService.record(new TaskNotificationEvent(UserNotificationType.TASK_INTERNAL_NOTE,
                nora, sara, project, task, null))).isEmpty();
    }

    @Test
    void participantsWhoLostProjectAccessAreNotNotified() throws Exception {
        Session admin = login("sara.saad@example.com"); Session peerSession = login(peer.email());
        long task = adminCreateTask(admin, nora);
        comment(peerSession, "/api/projects/mine/", task);
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, peer.id());

        comment(admin, "/api/projects/", task);

        assertThat(count(peer.id(), task, "TASK_COMMENTED")).isZero();
        assertThat(count(nora, task, "TASK_COMMENTED")).isEqualTo(2);
    }

    @Test
    void guestMessagesNotifyTheAssigneeOrEveryActiveAdmin() throws Exception {
        long otherAdmin = createUser("Guest Inbox Admin", "ADMIN", true);
        long inactiveAdmin = createUser("Inactive Admin", "ADMIN", false);
        Session noraSession = login("nora.ahmed@example.com");

        long assigned = guestTask(nora);
        guestMessage(assigned);
        assertThat(count(nora, assigned, "GUEST_MESSAGE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE request_id=?", Long.class, assigned)).isEqualTo(1);
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items[0].type").value("GUEST_MESSAGE"))
                .andExpect(jsonPath("$.items[0].guestName").value("Guest Person"))
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist());

        long unassigned = guestTask(null);
        guestMessage(unassigned);
        long activeAdmins = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND is_active", Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_notifications n JOIN users u ON u.user_id = n.recipient_id
                 WHERE n.request_id=? AND n.notification_type='GUEST_MESSAGE' AND u.role='ADMIN' AND u.is_active
                """, Long.class, unassigned)).isEqualTo(activeAdmins);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE request_id=?", Long.class, unassigned)).isEqualTo(activeAdmins);
        assertThat(count(otherAdmin, unassigned, "GUEST_MESSAGE")).isEqualTo(1);
        assertThat(count(inactiveAdmin, unassigned, "GUEST_MESSAGE")).isZero();
        assertThat(count(nora, unassigned, "GUEST_MESSAGE")).isZero();
    }

    @Test
    void liveStreamDeliversOnlyTheRecipientsOwnNotifications() throws Exception {
        Session admin = login("sara.saad@example.com"); Session noraSession = login("nora.ahmed@example.com");
        Session peerSession = login(peer.email());
        var noraStream = mvc.perform(get("/api/notifications/stream").session(noraSession.session()))
                .andExpect(request().asyncStarted()).andReturn();
        var peerStream = mvc.perform(get("/api/notifications/stream").session(peerSession.session()))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(noraStream.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(noraStream.getResponse().getHeader("Cache-Control")).contains("no-transform");
        assertThat(noraStream.getResponse().getContentAsString()).contains("event:ready");

        long task = adminCreateTask(admin, nora);

        String delivered = noraStream.getResponse().getContentAsString();
        assertThat(delivered).contains("event:notification").contains("\"taskId\":" + task)
                .contains("\"type\":\"TASK_ASSIGNED\"").contains("\"unreadCount\":");
        assertThat(peerStream.getResponse().getContentAsString()).doesNotContain("event:notification");
    }

    @Test
    void aNotificationFailureNeverRollsBackTheTaskChange() throws Exception {
        doThrow(new IllegalStateException("simulated notification failure")).when(notificationService).record(any());
        Session admin = login("sara.saad@example.com");

        long task = adminCreateTask(admin, nora);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, task)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE request_id=?", Long.class, task)).isZero();
    }

    private void comment(Session session, String prefix, long task) throws Exception {
        mvc.perform(write(post(prefix + project + "/tasks/" + task + "/comments"), session)
                .content("{\"text\":\"Participant comment\"}")).andExpect(status().is2xxSuccessful());
    }

    private void note(Session admin, long task) throws Exception {
        mvc.perform(write(post("/api/projects/" + project + "/tasks/" + task + "/internal-notes"), admin)
                .content("{\"text\":\"Admin-only note\"}")).andExpect(status().is2xxSuccessful());
    }

    private long count(long recipient, long task, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE recipient_id=? AND request_id=? AND notification_type=?",
                Long.class, recipient, task, type);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private String email(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE user_id=?", String.class, userId);
    }

    private long createUser(String name, String role, boolean active) {
        long id = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES (?, ?, (SELECT password_hash FROM users WHERE email='sara.saad@example.com'), ?, ?)
                RETURNING user_id
                """, Long.class, name, "test-" + role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com", role, active);
        users.add(id);
        return id;
    }

    private long guestTask(Long assignee) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,
                                     project_id,assigned_to,guest_name,guest_email)
                VALUES ('Guest task','Details',?,'MEDIUM',?,NULL,?,?,'Guest Person','guest-person@example.com')
                RETURNING request_id
                """, Long.class, workType, statusId("NEW"), project, assignee);
    }

    /** Seeds a verified Guest tracking session for the task and sends a message through the real endpoint. */
    private void guestMessage(long task) throws Exception {
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO guest_tracking_sessions(session_id,token_digest,request_id,last_activity_at,expires_at)
                VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                """, UUID.randomUUID(), GuestTrackingTestSupport.sessionDigest(guestCrypto, token), task);
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        mvc.perform(post("/api/guest/tracking/communications").cookie(new Cookie("MASAR_GUEST_TRACKING", token), csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello from the guest\"}"))
                .andExpect(status().isCreated());
    }

    private long adminCreateTask(Session admin, long assignee) throws Exception {
        String body = mvc.perform(write(post("/api/projects/" + project + "/tasks"), admin).content(taskBody(assignee)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String taskBody(long assignee) {
        return "{\"title\":\"Notify me\",\"description\":\"Details\",\"workTypeId\":" + workType
                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + assignee + "}";
    }

    private void expectNotifications(Session session, int count) throws Exception {
        mvc.perform(get("/api/notifications").session(session.session()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.items", hasSize(count)));
    }

    private List<Long> notificationIds(long recipient) {
        return jdbc.queryForList("SELECT notification_id FROM user_notifications WHERE recipient_id=? AND project_id=? ORDER BY notification_id",
                Long.class, recipient, project);
    }

    private long statusId(String semantic) {
        return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, project, semantic);
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Session session) {
        return builder.session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        var result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
