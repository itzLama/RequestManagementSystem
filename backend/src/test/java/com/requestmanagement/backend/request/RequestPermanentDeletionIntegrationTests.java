package com.requestmanagement.backend.request;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestPermanentDeletionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminDeletesGeneralRequestAndOwnedHistoryWithoutTouchingAnotherRequest() throws Exception {
        Session admin = login("sara.saad@example.com");
        long target = generalRequest("Deletion target");
        long unrelated = generalRequest("Deletion survivor");
        long adminId = userId("sara.saad@example.com");
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text) VALUES (?,?,?)", target, adminId, "Owned comment");
        jdbc.update("INSERT INTO status_history(request_id,old_status,new_status,changed_by) VALUES (?,'NEW','IN_PROGRESS',?)", target, adminId);
        jdbc.update("INSERT INTO guest_communications(request_id,sent_by,message,sender_type,sender_name_snapshot) VALUES (?,?,?,'TEAM',?)", target, adminId, "Owned message", "Sara Saad");

        mvc.perform(delete("/api/requests/admin/{id}", target).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());

        assertThat(count("requests", "request_id", target)).isZero();
        assertThat(count("comments", "request_id", target)).isZero();
        assertThat(count("status_history", "request_id", target)).isZero();
        assertThat(count("guest_communications", "request_id", target)).isZero();
        assertThat(count("requests", "request_id", unrelated)).isEqualTo(1);
    }

    @Test
    void adminDeletesGuestProjectTaskAndTrackingSessionNoLongerExists() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = createProject(admin, "Deletion Project");
        long workType = jdbc.queryForObject("SELECT work_type_id FROM project_work_types WHERE project_id=? AND work_type_name='Task'", Long.class, project);
        long statusId = jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'", Long.class, project);
        long task = jdbc.queryForObject("INSERT INTO requests(title,description,project_work_type_id,priority,status,project_status_id,guest_name,guest_email,project_id) VALUES ('Guest delete','Details',?,'MEDIUM','NEW',?,'Guest','guest@example.com',?) RETURNING request_id", Long.class, workType, statusId, project);
        UUID sessionId = UUID.randomUUID();
        jdbc.update("INSERT INTO guest_tracking_sessions(session_id,token_digest,request_id,last_activity_at,expires_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '30 minutes')", sessionId, "a".repeat(64), task);

        mvc.perform(delete("/api/projects/{project}/tasks/{task}", project, task).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());

        assertThat(count("requests", "request_id", task)).isZero();
        assertThat(count("guest_tracking_sessions", "request_id", task)).isZero();
        mvc.perform(get("/api/projects/{project}/tasks/{task}", project, task).session(admin.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeAndMissingCsrfCannotDelete() throws Exception {
        long request = generalRequest("Protected deletion");
        Session employee = login("nora.ahmed@example.com");
        Session admin = login("sara.saad@example.com");
        mvc.perform(delete("/api/requests/admin/{id}", request).session(employee.session()).cookie(employee.csrf())
                        .header("X-XSRF-TOKEN", employee.csrf().getValue())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/requests/admin/{id}", request).session(admin.session())).andExpect(status().isForbidden());
        assertThat(count("requests", "request_id", request)).isEqualTo(1);
    }

    private long generalRequest(String title) {
        long type = jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1", Long.class);
        long creator = userId("nora.ahmed@example.com");
        return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,priority,status,created_by) VALUES (?,'Details',?,'MEDIUM','NEW',?) RETURNING request_id", Long.class, title, type, creator);
    }

    private long createProject(Session admin, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"description\":null}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }

    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email); }
    private Cookie csrf() throws Exception { Cookie cookie=mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();return cookie; }
    private Session login(String email) throws Exception { Cookie csrf=csrf();MvcResult result=mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn();return new Session((MockHttpSession)result.getRequest().getSession(false),csrf); }
    private record Session(MockHttpSession session,Cookie csrf) { }
}
