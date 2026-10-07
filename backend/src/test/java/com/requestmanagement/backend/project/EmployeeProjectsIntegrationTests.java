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
class EmployeeProjectsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void memberDiscoversActiveAndArchivedProjectsButNotUnrelatedProjects() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long active = project("Employee active", "ACTIVE");
        long archived = project("Employee archived", "ARCHIVED");
        long unrelated = project("Employee unrelated", "ACTIVE");
        member(active, employee); member(archived, employee);
        Session session = login("nora.ahmed@example.com");

        mvc.perform(get("/api/projects/mine").session(session.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + archived + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + unrelated + ")]").isEmpty());
        mvc.perform(get("/api/projects/mine/{id}", active).session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(active));
        mvc.perform(get("/api/projects/mine/{id}", archived).session(session.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/projects/mine/{id}", unrelated).session(session.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedMembershipAndInactiveStaleSessionLoseAccess() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Membership access", "ACTIVE");
        member(project, employee); Session session = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/{id}", project).session(session.session())).andExpect(status().isOk());
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, employee);
        entityManager.clear();
        mvc.perform(get("/api/projects/mine/{id}", project).session(session.session())).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/mine/{id}/members", project).session(session.session())).andExpect(status().isNotFound());
        member(project, employee); jdbc.update("UPDATE users SET is_active=false WHERE user_id=?", employee); entityManager.clear();
        mvc.perform(get("/api/projects/mine").session(session.session())).andExpect(status().isForbidden());
    }

    @Test
    void employeeProjectEndpointsRequireEmployeeRole() throws Exception {
        mvc.perform(get("/api/projects/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/mine/{id}/members", 1)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/mine").session(login("sara.saad@example.com").session()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/{id}/members", 1)
                        .session(login("sara.saad@example.com").session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void retainedMemberReadsRealTeamDataForArchivedProjectIncludingInactiveMembers() throws Exception {
        long viewer = userId("nora.ahmed@example.com");
        long inactive = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES ('Retained Inactive','retained.inactive@example.com',
                        (SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',false)
                RETURNING user_id
                """, Long.class);
        long archived = project("Archived team visibility", "ARCHIVED");
        member(archived, viewer); member(archived, inactive);

        mvc.perform(get("/api/projects/mine/{id}/members", archived)
                        .session(login("nora.ahmed@example.com").session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeId == " + viewer + " && @.fullName == 'Nora Ahmed' && @.email == 'nora.ahmed@example.com')]").exists())
                .andExpect(jsonPath("$[?(@.employeeId == " + inactive + " && @.fullName == 'Retained Inactive' && @.email == 'retained.inactive@example.com' && @.active == false)]").exists());
    }

    @Test
    void unrelatedEmployeeCannotReadProjectMembers() throws Exception {
        long viewer = userId("nora.ahmed@example.com");
        long unrelated = createEmployee("Unrelated Viewer", "unrelated.viewer@example.com");
        long project = project("Protected team", "ACTIVE");
        member(project, viewer);

        mvc.perform(get("/api/projects/mine/{id}/members", project)
                        .session(login("unrelated.viewer@example.com").session()))
                .andExpect(status().isNotFound());
        assertThat(unrelated).isPositive();
    }

    private long project(String name, String status) { return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id", Long.class, name, status); }
    private void member(long project, long employee) { jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee); }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email); }
    private long createEmployee(String name, String email) { return jdbc.queryForObject("INSERT INTO users(full_name,email,password_hash,role,is_active) VALUES (?,?,(SELECT password_hash FROM users WHERE email='nora.ahmed@example.com'),'EMPLOYEE',true) RETURNING user_id", Long.class, name, email); }
    private Session login(String email) throws Exception { Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(csrf).isNotNull(); MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn(); return new Session((MockHttpSession) result.getRequest().getSession(false), csrf); }
    private record Session(MockHttpSession session, Cookie csrf) { }
}
