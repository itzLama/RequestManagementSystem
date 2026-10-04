package com.requestmanagement.backend.project;

import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMembershipIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void adminAddsListsAndRemovesActiveEmployeeWithoutChangingUser() throws Exception {
        long projectId = project("Team test"); long employeeId = employeeId();
        Session admin = login("sara.saad@example.com");
        add(projectId, employeeId, admin).andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").value(employeeId)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/projects/{id}/members", projectId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fullName").value("Nora Ahmed"));
        mvc.perform(delete("/api/projects/{id}/members/{employeeId}", projectId, employeeId)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id=? AND is_active=true", Long.class, employeeId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_memberships WHERE project_id=?", Long.class, projectId)).isZero();
    }

    @Test
    void invalidInactiveAdminNonexistentAndDuplicateMembersAreRejected() throws Exception {
        long projectId = project("Eligibility"); Session admin = login("sara.saad@example.com");
        long employeeId = employeeId(); add(projectId, employeeId, admin).andExpect(status().isCreated());
        add(projectId, employeeId, admin).andExpect(status().isConflict());
        long inactive = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES ('Inactive Member','inactive.member@example.com',(SELECT password_hash FROM users WHERE user_id=?),'EMPLOYEE',false)
                RETURNING user_id
                """, Long.class, employeeId);
        for (long invalid : new long[]{1L, inactive, 999999999L}) {
            add(projectId, invalid, admin).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_memberships WHERE project_id=?", Long.class, projectId)).isEqualTo(1L);
    }

    @Test
    void databaseConstraintAlsoPreventsDuplicateMembership() {
        long projectId = project("Constraint"); long employeeId = employeeId();
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", projectId, employeeId);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", projectId, employeeId));
    }

    @Test
    void archivedProjectFreezesTeamAndPreservesMembersIncludingLaterInactiveEmployees() throws Exception {
        long projectId = project("Archived team"); long employeeId = employeeId(); Session admin = login("sara.saad@example.com");
        add(projectId, employeeId, admin).andExpect(status().isCreated());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", projectId);
        jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", employeeId);
        entityManager.clear();
        mvc.perform(get("/api/projects/{id}/members", projectId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$[0].active").value(false));
        add(projectId, employeeId, admin).andExpect(status().isConflict());
        mvc.perform(delete("/api/projects/{id}/members/{employeeId}", projectId, employeeId)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_memberships WHERE project_id=?", Long.class, projectId)).isEqualTo(1L);
    }

    @Test
    void missingMembershipAndProjectReturnNotFound() throws Exception {
        Session admin = login("sara.saad@example.com"); long projectId = project("Missing membership");
        mvc.perform(delete("/api/projects/{id}/members/{employeeId}", projectId, employeeId())
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{id}/members", 999999999).session(admin.session())).andExpect(status().isNotFound());
    }

    @Test
    void membershipEndpointsAreAdminOnlyAndMutationsRequireCsrf() throws Exception {
        long projectId = project("Security");
        mvc.perform(get("/api/projects/{id}/members", projectId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/{id}/members", projectId).session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{id}/members", projectId).session(login("sara.saad@example.com").session())
                        .contentType("application/json").content("{\"employeeId\":" + employeeId() + "}"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions add(long projectId, long employeeId, Session session) throws Exception {
        return mvc.perform(post("/api/projects/{id}/members", projectId).session(session.session()).cookie(session.csrf())
                .header("X-XSRF-TOKEN", session.csrf().getValue()).contentType("application/json")
                .content("{\"employeeId\":" + employeeId + "}"));
    }
    private long project(String name) { return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name); }
    private long employeeId() { return jdbc.queryForObject("SELECT user_id FROM users WHERE email='nora.ahmed@example.com'", Long.class); }
    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(csrf).isNotNull();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
