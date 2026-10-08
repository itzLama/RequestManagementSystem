package com.requestmanagement.backend.guest;

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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestRequestIntegrationTests {
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(20);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anonymousGeneralSubmissionPersistsSafeIdentityAndSupportsAdminAssignment() throws Exception {
        Cookie csrf = csrf();
        long typeId = activeTypeId();
        MvcResult created = mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content("""
                                {"guestName":"  External Guest  ","guestEmail":"Guest@Example.COM ","title":" Guest access ",
                                 "description":" Please help ","typeId":%d,"priority":"HIGH",
                                 "status":"COMPLETED","assignedToId":1,"createdById":1}
                                """.formatted(typeId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestId").isNumber()).andReturn();
        long requestId = Long.parseLong(created.getResponse().getContentAsString().replaceAll("\\D", ""));
        var row = jdbc.queryForMap("SELECT * FROM requests WHERE request_id=?", requestId);
        assertThat(row.get("guest_name")).isEqualTo("External Guest");
        assertThat(row.get("guest_email")).isEqualTo("guest@example.com");
        assertThat(row.get("created_by")).isNull();
        assertThat(row.get("assigned_to")).isNull();
        assertThat(row.get("project_id")).isNull();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("priority")).isEqualTo("HIGH");
        assertThat(((Number) row.get("type_id")).longValue()).isEqualTo(typeId);

        Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/requests/admin/board").session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + requestId + " && @.guest == true)]").exists());
        long nora = userId("nora.ahmed@example.com");
        mvc.perform(patch("/api/requests/admin/{id}", requestId).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"status\":\"NEW\",\"assignedToId\":" + nora + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").value(nora));
        mvc.perform(get("/api/requests/assigned/board").session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + requestId + " && @.guest == true)]").exists());
    }

    @Test
    void anonymousProjectSubmissionIsUnassignedVisibleAssignableAndRecordsStatusHistory() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long projectId = project("Guest active project", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", projectId, nora);
        Cookie csrf = csrf();
        MvcResult created = mvc.perform(post("/api/guest/requests/projects/{projectId}", projectId).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content("""
                                {"guestName":"Project Guest","guestEmail":"project.guest@example.com","title":"Guest project bug",
                                 "description":"Broken flow","workType":"BUG","priority":"MEDIUM","assignedToId":1,"status":"COMPLETED"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        long requestId = Long.parseLong(created.getResponse().getContentAsString().replaceAll("\\D", ""));
        var row = jdbc.queryForMap("SELECT * FROM requests WHERE request_id=?", requestId);
        assertThat(row.get("created_by")).isNull();
        assertThat(row.get("assigned_to")).isNull();
        assertThat(((Number) row.get("project_id")).longValue()).isEqualTo(projectId);
        assertThat(row.get("type_id")).isNull();
        assertThat(row.get("work_type")).isEqualTo("BUG");
        assertThat(row.get("status")).isEqualTo("NEW");

        Session employee = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/board", projectId).session(employee.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + requestId + " && @.guest == true)]").exists());
        mvc.perform(get("/api/projects/mine/{projectId}/tasks/{id}", projectId, requestId).session(employee.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdById").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.requesterName").value("Project Guest"));
        mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{id}/assignee", projectId, requestId)
                        .session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"assignedToId\":" + nora + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedToId").value(nora));
        mvc.perform(patch("/api/projects/mine/{projectId}/tasks/{id}/status", projectId, requestId)
                        .session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"changeNote\":\"Accepted\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, requestId)).isEqualTo(1L);
    }

    @Test
    void publicListsAreMinimalAndArchivedProjectsRejectSubmission() throws Exception {
        long active = project("A Public Active", "ACTIVE");
        long archived = project("Z Hidden Archived", "ARCHIVED");
        mvc.perform(get("/api/guest/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active + ")].name").value("A Public Active"))
                .andExpect(jsonPath("$[?(@.id == " + archived + ")]").isEmpty())
                .andExpect(jsonPath("$[0].description").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
        mvc.perform(get("/api/guest/request-types")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber()).andExpect(jsonPath("$[0].name").isString())
                .andExpect(jsonPath("$[0].description").doesNotExist());
        Cookie csrf = csrf();
        mvc.perform(post("/api/guest/requests/projects/{id}", archived).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(projectBody("Archived")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/guest/requests/projects/{id}", Long.MAX_VALUE).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(projectBody("Missing")))
                .andExpect(status().isNotFound());
    }

    @Test
    void csrfValidationAndProtectedApisRemainEnforced() throws Exception {
        mvc.perform(post("/api/guest/requests/general").with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(generalBody("No csrf")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/guest/requests/general").cookie(new Cookie("XSRF-TOKEN", "cookie-token"))
                        .header("X-XSRF-TOKEN", "different-header-token").with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(generalBody("Bad csrf")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPublicInputsAreRejected() throws Exception {
        Cookie csrf = csrf();
        mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content("""
                                {"guestName":"Guest","guestEmail":"not-email","title":"Title","description":"Description","typeId":1,"priority":"LOW"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(generalBody("X").replace(activeTypeId() + "", Long.MAX_VALUE + "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/guest/requests/projects/1").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(projectBody("Bad enum").replace("\"BUG\"", "\"UNKNOWN\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitIsSharedAcrossGuestSubmissionEndpointsAndDifferentIpsAreIndependent() throws Exception {
        Cookie csrf = csrf();
        String limitedIp = uniqueIp();
        long projectId = project("Rate project", "ACTIVE");
        for (int index = 0; index < 4; index++) {
            mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                            .with(request -> { request.setRemoteAddr(limitedIp); return request; })
                            .contentType("application/json").content(generalBody("Rate " + index)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/guest/requests/projects/{id}", projectId).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(limitedIp); return request; })
                        .contentType("application/json").content(projectBody("Fifth")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(limitedIp); return request; })
                        .contentType("application/json").content(generalBody("Sixth")))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value("Too many guest submissions. Please try again later."));
        mvc.perform(post("/api/guest/requests/general").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .with(request -> { request.setRemoteAddr(uniqueIp()); return request; })
                        .contentType("application/json").content(generalBody("Other IP")))
                .andExpect(status().isCreated());
    }

    private String generalBody(String title) { return "{\"guestName\":\"Guest\",\"guestEmail\":\"guest@example.com\",\"title\":\"" + title + "\",\"description\":\"Description\",\"typeId\":" + activeTypeId() + ",\"priority\":\"LOW\"}"; }
    private String projectBody(String title) { return "{\"guestName\":\"Guest\",\"guestEmail\":\"guest@example.com\",\"title\":\"" + title + "\",\"description\":\"Description\",\"workType\":\"BUG\",\"priority\":\"LOW\"}"; }
    private long activeTypeId() { return jdbc.queryForObject("SELECT type_id FROM request_types WHERE is_active=true ORDER BY type_id LIMIT 1", Long.class); }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email); }
    private long project(String name, String status) { return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id", Long.class, name, status); }
    private String uniqueIp() { return "198.51.100." + IP_SEQUENCE.getAndIncrement(); }
    private Cookie csrf() throws Exception { Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(cookie).isNotNull(); return cookie; }
    private Session login(String email) throws Exception {
        Cookie csrf = csrf();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
