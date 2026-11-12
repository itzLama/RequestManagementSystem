package com.requestmanagement.backend.tasktemplate;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee;
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectTaskTemplateIntegrationTests {
    private static final String DOC = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Steps to reproduce\"}]}]}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void adminCreatesListsUpdatesAndDeletesBilingualTemplates() throws Exception {
        long project = project("Templates CRUD");
        long workType = workTypeId(jdbc, project, "Task");
        Session admin = login("sara.saad@example.com");

        String created = create(admin, project, body("Bug Report", "بلاغ خطأ", workType, "HIGH", "Bug: ", DOC, "3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bug Report"))
                .andExpect(jsonPath("$.nameAr").value("بلاغ خطأ"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.defaultTitle").value("Bug:"))
                .andExpect(jsonPath("$.description").value("Steps to reproduce"))
                .andExpect(jsonPath("$.dueOffsetDays").value(3))
                .andExpect(jsonPath("$.workTypeActive").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        create(admin, project, body("bug report", "مختلف", workType, "LOW", "x", DOC, "null"))
                .andExpect(status().isConflict());
        create(admin, project, body("Different", "بلاغ خطأ", workType, "LOW", "x", DOC, "null"))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/projects/{p}/task-templates/{t}", project, id).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json")
                        .content(body("Bug Report", "بلاغ خطأ", workType, "MEDIUM", "Defect", DOC, "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.dueOffsetDays").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/projects/{p}/task-templates", project).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/projects/{p}/task-templates/{t}", project, id).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_templates WHERE project_id=?", Long.class, project)).isZero();
    }

    @Test
    void rejectsInvalidInputForeignWorkTypesAndUnsafeDescriptions() throws Exception {
        long project = project("Templates validation"); long other = project("Templates other");
        long workType = workTypeId(jdbc, project, "Task"); long foreign = workTypeId(jdbc, other, "Task");
        Session admin = login("sara.saad@example.com");

        create(admin, project, body("Foreign", "أجنبي", foreign, "LOW", "x", DOC, "null")).andExpect(status().isBadRequest());
        create(admin, project, body("Blank Ar", " ", workType, "LOW", "x", DOC, "null")).andExpect(status().isBadRequest());
        create(admin, project, body("Offset", "إزاحة", workType, "LOW", "x", DOC, "366")).andExpect(status().isBadRequest());
        create(admin, project, body("Empty", "فارغ", workType, "LOW", "x", "{\"type\":\"doc\",\"content\":[]}", "null"))
                .andExpect(status().isBadRequest());
        create(admin, project, body("Unsafe", "غير آمن", workType, "LOW", "x",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\",\"marks\":[{\"type\":\"link\",\"attrs\":{\"href\":\"javascript:alert(1)\"}}]}]}]}",
                "null")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_templates WHERE project_id=?", Long.class, project)).isZero();
    }

    @Test
    void membersReadTemplatesButOnlyAdminsManageThemAndArchivedProjectsAreReadOnly() throws Exception {
        long nora = userId("nora.ahmed@example.com"); var outsiderFixture = createActiveEmployee(jdbc, "Template Outsider");
        long project = project("Templates access"); member(project, nora);
        long workType = workTypeId(jdbc, project, "Task");
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com");
        Session outsider = login(outsiderFixture.email());
        create(admin, project, body("Feature Request", "طلب ميزة", workType, "MEDIUM", "Feature: ", DOC, "7"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/projects/mine/{p}/task-templates", project).session(member.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nameAr").value("طلب ميزة"));
        mvc.perform(get("/api/projects/mine/{p}/task-templates", project).session(outsider.session()))
                .andExpect(status().isNotFound());
        create(member, project, body("Sneaky", "متسلل", workType, "LOW", "x", DOC, "null")).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/{p}/task-templates", project).session(admin.session())
                        .contentType("application/json").content(body("No CSRF", "بدون", workType, "LOW", "x", DOC, "null")))
                .andExpect(status().isForbidden());

        long id = jdbc.queryForObject("SELECT template_id FROM project_task_templates WHERE project_id=?", Long.class, project);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project); entityManager.clear();
        mvc.perform(get("/api/projects/mine/{p}/task-templates", project).session(member.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        create(admin, project, body("Late", "متأخر", workType, "LOW", "x", DOC, "null")).andExpect(status().isConflict());
        mvc.perform(delete("/api/projects/{p}/task-templates/{t}", project, id).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
    }

    @Test
    void referencedWorkTypeIsRetiredNotDeletedAndProjectDeletionRemovesTemplates() throws Exception {
        long project = project("Templates work type");
        long keep = workTypeId(jdbc, project, "Task");
        long used = jdbc.queryForObject("INSERT INTO project_work_types(project_id,work_type_name) VALUES (?,'Bug') RETURNING work_type_id", Long.class, project);
        Session admin = login("sara.saad@example.com");
        create(admin, project, body("Bug Report", "بلاغ خطأ", used, "HIGH", "Bug", DOC, "null")).andExpect(status().isCreated());

        mvc.perform(delete("/api/projects/{p}/work-types/{w}", project, used).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT is_active FROM project_work_types WHERE work_type_id=?", Boolean.class, used)).isFalse();
        entityManager.clear();
        mvc.perform(get("/api/projects/{p}/task-templates", project).session(admin.session()))
                .andExpect(jsonPath("$[0].workTypeActive").value(false));
        assertThat(keep).isPositive();

        mvc.perform(delete("/api/projects/{p}", project).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_templates WHERE project_id=?", Long.class, project)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE project_id=?", Long.class, project)).isZero();
    }

    private String body(String name, String nameAr, long workType, String priority, String title, String doc, String offset) {
        return "{\"name\":\"" + name + "\",\"nameAr\":\"" + nameAr + "\",\"workTypeId\":" + workType
                + ",\"priority\":\"" + priority + "\",\"defaultTitle\":\"" + title + "\",\"descriptionDocument\":" + doc
                + ",\"dueOffsetDays\":" + offset + "}";
    }

    private ResultActions create(Session s, long project, String body) throws Exception {
        return mvc.perform(post("/api/projects/{p}/task-templates", project).session(s.session())
                .cookie(s.csrf()).header("X-XSRF-TOKEN", s.csrf().getValue())
                .contentType("application/json").content(body));
    }

    private long project(String name) {
        return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id", Long.class, name);
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
