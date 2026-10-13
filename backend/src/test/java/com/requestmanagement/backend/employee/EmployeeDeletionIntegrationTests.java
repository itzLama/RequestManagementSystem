package com.requestmanagement.backend.employee;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeDeletionIntegrationTests {
    private static final String CONFLICT_MESSAGE =
            "Employee has persisted activity and must be deactivated instead.";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminDeletesUnusedEmployeeWhoDisappearsAndCannotAuthenticate() throws Exception {
        Employee employee = employee("Unused Employee");
        Session admin = login("sara.saad@example.com");

        deleteEmployee(employee.id(), admin).andExpect(status().isNoContent());

        assertThat(userCount(employee.id())).isZero();
        mvc.perform(get("/api/employees/{id}", employee.id()).session(admin.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/employees").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + employee.id() + ")]").isEmpty());
        loginRequest(employee.email(), csrf()).andExpect(status().isUnauthorized());
    }

    @Test
    void createdRequestOrTaskBlocksDeletionAndRecordsRemain() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee requestCreator = employee("Request Creator");
        Employee taskCreator = employee("Task Creator");
        long request = generalRequest(requestCreator.id(), null);
        long task = projectTask(taskCreator.id(), null);

        assertConflict(requestCreator.id(), admin);
        assertConflict(taskCreator.id(), admin);

        assertThat(requestCount(request)).isOne();
        assertThat(requestCount(task)).isOne();
        assertThat(userCount(requestCreator.id())).isOne();
        assertThat(userCount(taskCreator.id())).isOne();
    }

    @Test
    void currentRequestOrTaskAssignmentBlocksDeletionAndRecordsRemain() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee requestAssignee = employee("Request Assignee");
        Employee taskAssignee = employee("Task Assignee");
        long request = generalRequest(adminUserId(), requestAssignee.id());
        long task = projectTask(adminUserId(), taskAssignee.id());

        assertConflict(requestAssignee.id(), admin);
        assertConflict(taskAssignee.id(), admin);

        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, request))
                .isEqualTo(requestAssignee.id());
        assertThat(jdbc.queryForObject("SELECT assigned_to FROM requests WHERE request_id=?", Long.class, task))
                .isEqualTo(taskAssignee.id());
    }

    @Test
    void authoredCommentsAndInternalNotesBlockDeletionAndRemain() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee author = employee("Comment Author");
        long request = generalRequest(adminUserId(), null);
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal) VALUES (?,?,?,false)",
                request, author.id(), "Public history");
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal) VALUES (?,?,?,true)",
                request, author.id(), "Internal history");

        assertConflict(author.id(), admin);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE user_id=?", Integer.class, author.id()))
                .isEqualTo(2);
    }

    @Test
    void statusHistoryBlocksDeletionAndRemains() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee actor = employee("Status Actor");
        long request = generalRequest(adminUserId(), null);
        jdbc.update("INSERT INTO status_history(request_id,old_status,new_status,changed_by,change_note) VALUES (?,'NEW','IN_PROGRESS',?,?)",
                request, actor.id(), "Started work");

        assertConflict(actor.id(), admin);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE changed_by=?", Integer.class, actor.id()))
                .isOne();
    }

    @Test
    void guestCommunicationBlocksDeletionAndRemains() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee sender = employee("Guest Sender");
        long request = guestRequest();
        jdbc.update("INSERT INTO guest_communications(request_id,sent_by,message) VALUES (?,?,?)",
                request, sender.id(), "Guest update");

        assertConflict(sender.id(), admin);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guest_communications WHERE sent_by=?", Integer.class, sender.id()))
                .isOne();
    }

    @Test
    void projectMembershipBlocksDeletionAndRemains() throws Exception {
        Session admin = login("sara.saad@example.com");
        Employee member = employee("Project Member");
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Deletion policy " + UUID.randomUUID());
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, member.id());

        assertConflict(member.id(), admin);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_memberships WHERE employee_id=?", Integer.class, member.id()))
                .isOne();
    }

    @Test
    void adminNonAdminMissingTargetAndCsrfRulesAreEnforced() throws Exception {
        Session admin = login("sara.saad@example.com");
        Session employee = login("nora.ahmed@example.com");
        Employee target = employee("Authorization Target");

        deleteEmployee(adminUserId(), admin).andExpect(status().isNotFound());
        deleteEmployee(999999999L, admin).andExpect(status().isNotFound());
        deleteEmployee(target.id(), employee).andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/{id}", target.id()).session(admin.session()))
                .andExpect(status().isForbidden());
        assertThat(userCount(target.id())).isOne();
    }

    private void assertConflict(long employeeId, Session admin) throws Exception {
        deleteEmployee(employeeId, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CONFLICT_MESSAGE));
    }

    private org.springframework.test.web.servlet.ResultActions deleteEmployee(long id, Session session)
            throws Exception {
        return mvc.perform(delete("/api/employees/{id}", id).session(session.session())
                .cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    private Employee employee(String name) {
        String email = "delete-" + UUID.randomUUID() + "@example.com";
        long id = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES (?,?,(SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',true)
                RETURNING user_id
                """, Long.class, name, email);
        return new Employee(id, email);
    }

    private long generalRequest(long creator, Long assignee) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by,assigned_to)
                VALUES (?,'Deletion policy test',(SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1),
                        'LOW','NEW',?,?) RETURNING request_id
                """, Long.class, "Deletion request " + UUID.randomUUID(), creator, assignee);
    }

    private long projectTask(long creator, Long assignee) {
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",
                Long.class, "Deletion project " + UUID.randomUUID());
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,created_by,assigned_to,project_id)
                VALUES (?,'Deletion policy task',NULL,?,'LOW','NEW',?,?,?) RETURNING request_id
                """, Long.class, "Deletion task " + UUID.randomUUID(), workTypeId(jdbc, project, "Task"), creator, assignee, project);
    }

    private long guestRequest() {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,priority,status,created_by,guest_name,guest_email)
                VALUES (?,'Guest deletion policy test',(SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1),
                        'LOW','NEW',NULL,'Guest User','guest@example.com') RETURNING request_id
                """, Long.class, "Guest request " + UUID.randomUUID());
    }

    private int userCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=?", Integer.class, id);
    }

    private int requestCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Integer.class, id);
    }

    private long adminUserId() {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email='sara.saad@example.com'", Long.class);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = csrf();
        MvcResult result = loginRequest(email, csrf).andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private Cookie csrf() throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private org.springframework.test.web.servlet.ResultActions loginRequest(String email, Cookie csrf) throws Exception {
        return mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"));
    }

    private record Employee(long id, String email) { }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
