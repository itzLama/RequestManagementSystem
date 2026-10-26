package com.requestmanagement.backend.projectstatus;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectStatusIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void projectsReceiveOrderedDefaultsAndNewIsProtected() throws Exception {
        long project = project("Status defaults", "ACTIVE");
        Session admin = login("sara.saad@example.com");

        mvc.perform(get("/api/projects/{id}/statuses", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].name").value("New"))
                .andExpect(jsonPath("$[0].protectedNew").value(true))
                .andExpect(jsonPath("$[4].name").value("Rejected"));

        long newId = statusId(project, "NEW");
        mvc.perform(put("/api/projects/{project}/statuses/{status}", project, newId)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Backlog\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/projects/{project}/statuses/{status}", project, newId)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanCreateRenameAndReorderWhileValidationAndAuthorizationAreEnforced() throws Exception {
        long project = project("Status configuration", "ACTIVE");
        Session admin = login("sara.saad@example.com");
        MvcResult created = mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"  Quality Review  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Quality Review")).andReturn();
        long custom = number(created, "$.id");

        mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"quality review\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/projects/{project}/statuses/{status}", project, custom)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Verification\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Verification"));

        List<Long> ids = jdbc.queryForList("SELECT status_id FROM project_status_columns WHERE project_id=? AND is_active ORDER BY display_order", Long.class, project);
        List<Long> reordered = List.of(ids.get(0), custom, ids.get(1), ids.get(2), ids.get(3), ids.get(4));
        mvc.perform(put("/api/projects/{id}/statuses/order", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"statusIds\":" + reordered + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].id").value(custom));

        Session employee = login("nora.ahmed@example.com");
        mvc.perform(post("/api/projects/{id}/statuses", project).session(employee.session())
                        .cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{id}/statuses", project).session(admin.session())
                        .contentType("application/json").content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanInsertStatusBetweenColumnsWhileNewRemainsFirst() throws Exception {
        long project = project("Status insertion", "ACTIVE");
        Session admin = login("sara.saad@example.com");
        MvcResult created = mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Triage\",\"position\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.displayOrder").value(1)).andReturn();
        long inserted = number(created, "$.id");

        List<Long> ordered = jdbc.queryForList("SELECT status_id FROM project_status_columns WHERE project_id=? AND is_active ORDER BY display_order,status_id", Long.class, project);
        assertThat(ordered.getFirst()).isEqualTo(statusId(project, "NEW"));
        assertThat(ordered.get(1)).isEqualTo(inserted);

        mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Before New\",\"position\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void maximumOfTenActiveColumnsIsStillEnforced() throws Exception {
        long project = project("Status maximum", "ACTIVE");
        for (int order = 5; order < 10; order++) {
            jdbc.update("INSERT INTO project_status_columns(project_id,status_name,display_order) VALUES (?,?,?)", project, "Custom " + order, order);
        }
        Session admin = login("sara.saad@example.com");
        mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Too Many\",\"position\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A project can have up to 10 status columns."));
    }

    @Test
    void populatedColumnMovesTasksTransactionallyBeforeRetirement() throws Exception {
        long project = project("Status deletion", "ACTIVE");
        long actor = userId("nora.ahmed@example.com");
        long task = task(project, actor, "IN_PROGRESS");
        long source = statusId(project, "IN_PROGRESS");
        long replacement = statusId(project, "WAITING_USER");
        Session admin = login("sara.saad@example.com");

        long otherProject = project("Wrong destination", "ACTIVE");
        mvc.perform(delete("/api/projects/{project}/statuses/{status}", project, source)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"mode\":\"MOVE_TASKS\",\"replacementStatusId\":" + statusId(otherProject, "WAITING_USER") + "}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT project_status_id FROM requests WHERE request_id=?", Long.class, task)).isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_status_columns WHERE status_id=?", Boolean.class, source)).isTrue();

        mvc.perform(delete("/api/projects/{project}/statuses/{status}", project, source)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"mode\":\"MOVE_TASKS\",\"replacementStatusId\":" + replacement + "}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT project_status_id FROM requests WHERE request_id=?", Long.class, task)).isEqualTo(replacement);
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_status_columns WHERE status_id=?", Boolean.class, source)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Integer.class, task)).isOne();
    }

    @Test
    void permanentDeletionRemovesOnlySelectedStatusTasksAndTheirDependencies() throws Exception {
        long project = project("Permanent task deletion", "ACTIVE");
        long otherProject = project("Deletion isolation", "ACTIVE");
        long actor = userId("nora.ahmed@example.com");
        long source = statusId(project, "IN_PROGRESS");
        long selected = task(project, actor, "IN_PROGRESS");
        long retained = task(project, actor, "WAITING_USER");
        long other = task(otherProject, actor, "IN_PROGRESS");
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text,is_internal) VALUES (?,?,?,false)", selected, actor, "Delete with task");
        jdbc.update("INSERT INTO status_history(request_id,old_project_status_id,new_project_status_id,changed_by) VALUES (?,?,?,?)", selected, statusId(project, "NEW"), source, actor);
        Session admin = login("sara.saad@example.com");

        mvc.perform(delete("/api/projects/{project}/statuses/{status}", project, source)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"mode\":\"DELETE_TASKS\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Integer.class, selected)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Integer.class, selected)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Integer.class, selected)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?)", Integer.class, retained, other)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_status_columns WHERE status_id=?", Boolean.class, source)).isFalse();
    }

    @Test
    void renamedThenRetiredStatusRemainsReadableInHistory() throws Exception {
        long project = project("Status history names", "ACTIVE");
        long actor = userId("nora.ahmed@example.com");
        long task = task(project, actor, "NEW");
        Session admin = login("sara.saad@example.com");
        MvcResult created = mvc.perform(post("/api/projects/{id}/statuses", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Quality Review\"}"))
                .andExpect(status().isCreated()).andReturn();
        long custom = number(created, "$.id");
        long completed = statusId(project, "COMPLETED");

        mvc.perform(patch("/api/projects/{project}/tasks/{task}/status", project, task)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"statusId\":" + custom + "}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/projects/{project}/tasks/{task}/status", project, task)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"statusId\":" + completed + "}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/projects/{project}/statuses/{status}", project, custom)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Final Review\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/projects/{project}/statuses/{status}", project, custom)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/{project}/tasks/{task}", project, task).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[0].newProjectStatusName").value("Final Review"))
                .andExpect(jsonPath("$.timeline[0].changeNote").value("Status changed from New to Final Review."))
                .andExpect(jsonPath("$.timeline[1].oldProjectStatusName").value("Final Review"))
                .andExpect(jsonPath("$.timeline[1].changeNote").value("Status changed from Final Review to Completed."));
    }

    @Test
    void crossProjectInactiveAndArchivedStatusChangesAreRejected() throws Exception {
        long first = project("Status scope A", "ACTIVE");
        long second = project("Status scope B", "ACTIVE");
        long nora = userId("nora.ahmed@example.com");
        member(first, nora);
        long task = task(first, nora, "NEW");
        Session employee = login("nora.ahmed@example.com");

        mvc.perform(patch("/api/projects/mine/{project}/tasks/{task}/status", first, task)
                        .session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"statusId\":" + statusId(second, "COMPLETED") + "}"))
                .andExpect(status().isNotFound());

        Session admin = login("sara.saad@example.com");
        mvc.perform(patch("/api/projects/{project}/archive", first).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/projects/mine/{project}/tasks/{task}/status", first, task)
                        .session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue())
                        .contentType("application/json").content("{\"statusId\":" + statusId(first, "COMPLETED") + "}"))
                .andExpect(status().isConflict());
    }

    private long project(String name, String status) { return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id", Long.class, name, status); }
    private long userId(String email) { return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email); }
    private long statusId(long project, String semantic) { return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?", Long.class, project, semantic); }
    private void member(long project, long employee) { jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, employee); }
    private long task(long project, long creator, String semantic) { return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,created_by,project_id) VALUES ('Status task','Test',NULL,?,'MEDIUM',?,?,?) RETURNING request_id", Long.class, workTypeId(jdbc, project, "Task"), semantic, creator, project); }
    private long number(MvcResult result, String path) throws Exception { return Long.parseLong(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path).toString()); }
    private Session login(String email) throws Exception { Cookie csrf=mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN"); assertThat(csrf).isNotNull(); MvcResult result=mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn(); return new Session((MockHttpSession)result.getRequest().getSession(false),csrf); }
    private record Session(MockHttpSession session, Cookie csrf) {}
}
