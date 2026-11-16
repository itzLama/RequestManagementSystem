package com.requestmanagement.backend.projectworktype;

import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
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
class ProjectWorkTypeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void deletingUnusedWorkTypeRemovesItWhileUsedWorkTypeIsRetired() throws Exception {
        Session admin = login("sara.saad@example.com");
        long project = project("Work Type Deletion", "ACTIVE");
        long unused = createType(admin, project, "Unused Type");
        long used = createType(admin, project, "Used Type");
        createType(admin, project, "Remaining Type");

        retire(admin, project, unused);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_work_types WHERE work_type_id=?", Long.class, unused)).isZero();

        task(project, used);
        retire(admin, project, used);
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_work_types WHERE work_type_id=?", Boolean.class, used)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE project_work_type_id=?", Long.class, used)).isEqualTo(1);
    }

    @Test
    void newProjectReceivesExactlyThreeDefaults() throws Exception {
        Session admin = login("sara.saad@example.com");
        MvcResult result = mvc.perform(post("/api/projects").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"Defaults Project\",\"description\":null}"))
                .andExpect(status().isCreated()).andReturn();
        long projectId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(get("/api/projects/{projectId}/work-types", projectId).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.name == 'Task' && @.nameAr == 'مهمة' && @.active == true)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Bug' && @.nameAr == 'خلل' && @.active == true)]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Improvement' && @.nameAr == 'تحسين' && @.active == true)]").exists());
    }

    @Test
    void adminCreatesAndRenamesBilingualTypeWhileLegacyTypeFallsBackToEnglish() throws Exception {
        long project = project("Bilingual Types", "ACTIVE");
        Session admin = login("sara.saad@example.com");

        MvcResult created = mvc.perform(post("/api/projects/{project}/work-types", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"IT Support\",\"nameAr\":\"الدعم الفني\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("IT Support"))
                .andExpect(jsonPath("$.nameAr").value("الدعم الفني"))
                .andReturn();
        long bilingualId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(put("/api/projects/{project}/work-types/{type}", project, bilingualId)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content("{\"name\":\"Service Desk\",\"nameAr\":\"مكتب الخدمة\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Service Desk"))
                .andExpect(jsonPath("$.nameAr").value("مكتب الخدمة"));

        long legacyId = createType(admin, project, "Legacy English Only");
        mvc.perform(get("/api/projects/{project}/work-types", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + legacyId + " && @.name == 'Legacy English Only' && @.nameAr == null)]").exists());
    }

    @Test
    void adminAddsRenamesAndRetiresWhileExistingTaskKeepsAssociation() throws Exception {
        long project = project("Lifecycle Project", "ACTIVE");
        Session admin = login("sara.saad@example.com");
        long custom = createType(admin, project, "  Technical Support  ");
        createType(admin, project, "Task");
        long task = task(project, custom);

        mvc.perform(put("/api/projects/{project}/work-types/{type}", project, custom)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Service Desk\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(custom))
                .andExpect(jsonPath("$.name").value("Service Desk"));
        mvc.perform(delete("/api/projects/{project}/work-types/{type}", project, custom)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/{project}/work-types", project).session(admin.session()))
                .andExpect(jsonPath("$[?(@.id == " + custom + " && @.name == 'Service Desk' && @.active == false)]").exists());
        mvc.perform(get("/api/guest/projects/{project}/work-types", project))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + custom + ")]").isEmpty());
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"service desk\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/projects/{project}/tasks/{task}", project, task).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workTypeId").value(custom))
                .andExpect(jsonPath("$.workTypeName").value("Service Desk"));
    }

    @Test
    void uniquenessValidationFinalTypeAndArchivedProjectAreEnforced() throws Exception {
        long project = project("Rules Project", "ACTIVE");
        Session admin = login("sara.saad@example.com");
        long only = createType(admin, project, "Operations");
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\" operations \"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/projects/{project}/work-types/{type}", project, only)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        mvc.perform(put("/api/projects/{project}/work-types/{type}", project, only)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"Archived Rename\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void managementRequiresAdminAndCsrf() throws Exception {
        long project = project("Secured Types", "ACTIVE");
        Session employee = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/{project}/work-types", project).session(employee.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{project}/work-types", project).session(login("sara.saad@example.com").session())
                        .contentType("application/json").content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeAndGuestListsAreActiveScopedAndProjectAuthorized() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Shared Types", "ACTIVE");
        long other = project("Other Types", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, nora);
        Session admin = login("sara.saad@example.com");
        long visible = createType(admin, project, "Design Review");
        long hidden = createType(admin, project, "Retired Choice");
        retire(admin, project, hidden);
        long otherType = createType(admin, other, "Other Project Choice");
        Session employee = login("nora.ahmed@example.com");

        mvc.perform(get("/api/projects/mine/{project}/work-types", project).session(employee.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + visible + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + hidden + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + otherType + ")]").isEmpty());
        mvc.perform(get("/api/projects/mine/{project}/work-types", other).session(employee.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/guest/projects/{project}/work-types", project))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + visible + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + hidden + ")]").isEmpty());
    }

    @Test
    void allCreationFlowsUseCustomTypeAndRejectWrongProjectOrRetiredType() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Creation Types", "ACTIVE");
        long other = project("Wrong Scope Types", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, nora);
        Session admin = login("sara.saad@example.com");
        long custom = createType(admin, project, "Feature Request");
        long retired = createType(admin, project, "Legacy Choice");
        long foreign = createType(admin, other, "Foreign Choice");
        retire(admin, project, retired);

        mvc.perform(post("/api/projects/{project}/tasks", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content(taskBody("Admin custom", custom, nora)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workTypeName").value("Feature Request"));
        Session employee = login("nora.ahmed@example.com");
        mvc.perform(post("/api/projects/mine/{project}/tasks", project).session(employee.session()).cookie(employee.csrf())
                        .header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json")
                        .content(taskBody("Employee custom", custom, nora)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workTypeId").value(custom));
        Cookie guestCsrf = csrf();
        mvc.perform(post("/api/guest/requests/projects/{project}", project).cookie(guestCsrf)
                        .header("X-XSRF-TOKEN", guestCsrf.getValue()).with(request -> { request.setRemoteAddr("203.0.113.240"); return request; })
                        .contentType("application/json").content("{\"guestName\":\"Work Type Guest\",\"guestEmail\":\"types@example.com\",\"title\":\"Guest custom\",\"description\":\"Project request\",\"workTypeId\":" + custom + ",\"priority\":\"LOW\"}"))
                .andExpect(status().isCreated());
        for (long invalid : new long[]{foreign, retired, Long.MAX_VALUE}) {
            mvc.perform(post("/api/projects/{project}/tasks", project).session(admin.session()).cookie(admin.csrf())
                            .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                            .content(taskBody("Invalid type", invalid, nora)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void adminRestoresRetiredTypeInPlaceAndItIsSelectableAgain() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Restore Types", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, nora);
        Session admin = login("sara.saad@example.com");
        createType(admin, project, "Keeper");
        long retired = createBilingualType(admin, project, "Task", "مهمة");
        long historical = task(project, retired);
        retire(admin, project, retired);

        // Adding the same name (any case, either language) points to Restore instead of creating a duplicate.
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\" task \",\"nameAr\":\"مهمة جديدة\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ProjectWorkTypeService.RETIRED_NAME_MESSAGE));
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"Other Task\",\"nameAr\":\"مهمة\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ProjectWorkTypeService.RETIRED_ARABIC_NAME_MESSAGE));

        restore(admin, project, retired).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(retired)).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.name").value("Task")).andExpect(jsonPath("$.nameAr").value("مهمة"));
        // Idempotent: restoring an active type is a no-op.
        restore(admin, project, retired).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(retired)).andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_work_types WHERE project_id=? AND LOWER(work_type_name)='task'",
                Long.class, project)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT project_work_type_id FROM requests WHERE request_id=?", Long.class, historical))
                .isEqualTo(retired);

        // Duplicate names are still rejected once the type is active again (existing message).
        mvc.perform(post("/api/projects/{project}/work-types", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"TASK\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A Work Type with this name already exists in the Project."));

        // Selectable again: member list, Create Task and Task Templates.
        Session employee = login("nora.ahmed@example.com");
        mvc.perform(get("/api/projects/mine/{project}/work-types", project).session(employee.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + retired + ")]").exists());
        mvc.perform(post("/api/projects/{project}/tasks", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content(taskBody("Restored type task", retired, nora)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workTypeId").value(retired));
        mvc.perform(post("/api/projects/{project}/task-templates", project).session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json")
                        .content("{\"name\":\"Restored Template\",\"nameAr\":\"قالب\",\"workTypeId\":" + retired
                                + ",\"priority\":\"LOW\",\"defaultTitle\":\"From template\",\"descriptionDocument\":"
                                + "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}]}"
                                + ",\"dueOffsetDays\":null}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workTypeId").value(retired));
    }

    @Test
    void restoreRequiresAdminCsrfOwnedTypeAndActiveProject() throws Exception {
        long nora = userId("nora.ahmed@example.com");
        long project = project("Restore Rules", "ACTIVE");
        long other = project("Restore Other", "ACTIVE");
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, nora);
        Session admin = login("sara.saad@example.com");
        createType(admin, project, "Keeper");
        long retired = createType(admin, project, "Retired One");
        task(project, retired);
        retire(admin, project, retired);

        Session employee = login("nora.ahmed@example.com");
        restore(employee, project, retired).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{project}/work-types/{type}/restore", project, retired).session(admin.session()))
                .andExpect(status().isForbidden());
        restore(admin, other, retired).andExpect(status().isNotFound());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        entityManager.clear();
        restore(admin, project, retired).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_work_types WHERE work_type_id=?", Boolean.class, retired)).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions restore(Session session, long project, long type) throws Exception {
        return mvc.perform(post("/api/projects/{project}/work-types/{type}/restore", project, type)
                .session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue()));
    }

    private long createBilingualType(Session admin, long project, String name, String nameAr) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{project}/work-types", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"" + name + "\",\"nameAr\":\"" + nameAr + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long project(String name, String status) {
        Long id = jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",
                Long.class, name, status);
        jdbc.update("INSERT INTO project_guest_form_settings(project_id) VALUES (?)", id);
        return id;
    }

    private long createType(Session admin, long project, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{project}/work-types", project)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void retire(Session admin, long project, long type) throws Exception {
        mvc.perform(delete("/api/projects/{project}/work-types/{type}", project, type)
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
    }

    private long task(long project, long workType) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,type_id,project_work_type_id,priority,status,created_by,project_id)
                VALUES ('Historical Work Type','History test',NULL,?,'MEDIUM','NEW',?,?) RETURNING request_id
                """, Long.class, workType, userId("nora.ahmed@example.com"), project);
    }

    private String taskBody(String title, long workType, long assignee) {
        return "{\"title\":\"" + title + "\",\"description\":\"Project work\",\"workTypeId\":" + workType
                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + assignee + "}";
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }

    private Cookie csrf() throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private Session login(String email) throws Exception {
        Cookie csrf = csrf();
        MvcResult result = mvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return new Session((MockHttpSession) result.getRequest().getSession(false), csrf);
    }

    private record Session(MockHttpSession session, Cookie csrf) { }
}
