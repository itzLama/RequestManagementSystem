package com.requestmanagement.backend.planning;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectRoadmapIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectRoadmapService service;

    @Test
    void spansUseRootTaskDatesOnlyAndOverdueExcludesCompletedStatuses() throws Exception {
        LocalDate today = service.today();
        assertThat(today).isEqualTo(LocalDate.now(ZoneId.of("Asia/Riyadh")));
        long nora = userId("nora.ahmed@example.com");
        long project = project("Roadmap span " + System.nanoTime()); member(project, nora);
        long open = task(project, nora, null, today.minusDays(5), today.minusDays(2), "NEW");
        task(project, nora, null, null, today.minusDays(1), "COMPLETED");
        task(project, nora, null, today.plusDays(3), null, "NEW");
        task(project, nora, null, null, null, "NEW");
        task(project, nora, open, today.minusDays(60), today.plusDays(60), "NEW");
        Session admin = login("sara.saad@example.com");

        String path = "$.projects[?(@.projectId == " + project + ")]";
        mvc.perform(get("/api/projects/roadmap").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath(path + ".startDate").value(today.minusDays(5).toString()))
                .andExpect(jsonPath(path + ".endDate").value(today.plusDays(3).toString()))
                .andExpect(jsonPath(path + ".totalTasks").value(4))
                .andExpect(jsonPath(path + ".scheduledTasks").value(3))
                .andExpect(jsonPath(path + ".overdueTasks").value(1));
    }

    @Test
    void adminsSeeAllProjectsEmployeesOnlyTheirOwnAndAccessIsEnforced() throws Exception {
        long nora = userId("nora.ahmed@example.com"); var outsider = createActiveEmployee(jdbc, "Roadmap Outsider");
        long mine = project("Roadmap mine " + System.nanoTime()); member(mine, nora);
        long other = project("Roadmap other " + System.nanoTime());
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");
        Session stranger = login(outsider.email());

        mvc.perform(get("/api/projects/roadmap").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[?(@.projectId == " + other + ")].startDate").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.projects[?(@.projectId == " + mine + ")]").isNotEmpty());
        mvc.perform(get("/api/projects/mine/roadmap").session(member.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[?(@.projectId == " + mine + ")]").isNotEmpty())
                .andExpect(jsonPath("$.projects[?(@.projectId == " + other + ")]").isEmpty());
        mvc.perform(get("/api/projects/mine/roadmap").session(stranger.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projects.length()").value(0));
        mvc.perform(get("/api/projects/roadmap").session(member.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/roadmap").session(admin.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/roadmap")).andExpect(status().isUnauthorized());
    }

    private long project(String name) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name);
    }

    private long task(long project, long user, Long parent, LocalDate start, LocalDate due, String semantic) {
        long workType = workTypeId(jdbc, project, "Task");
        long status = jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",
                Long.class, project, semantic);
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,
                                     project_id,assigned_to,parent_request_id,start_date,due_date)
                VALUES ('Roadmap','Details',?,'MEDIUM',?,?,?,?,?,?,?) RETURNING request_id
                """, Long.class, workType, status, user, project, user, parent,
                start == null ? null : Date.valueOf(start), due == null ? null : Date.valueOf(due));
    }

    private void member(long project, long employee) {
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Session login(String email) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        var result = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
