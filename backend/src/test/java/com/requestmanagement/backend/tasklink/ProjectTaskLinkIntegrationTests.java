package com.requestmanagement.backend.tasklink;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectTaskLinkIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void storesCanonicalDirectionalAndSymmetricLinksAndReturnsPerspectiveLabels() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Link semantics"); member(project, employee);
        long first = task(project, employee, null, "First");
        long second = task(project, employee, null, "Second");
        long child = task(project, employee, first, "Child");
        Session admin = login("sara.saad@example.com");

        create(admin, project, first, second, "BLOCKS");
        create(admin, project, first, child, "IS_DUPLICATED_BY");
        create(admin, project, second, child, "RELATES_TO");

        mvc.perform(get("/api/projects/{p}/tasks/{t}/links", project, second).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relatedTaskId == " + first + ")].relationshipType").value("IS_BLOCKED_BY"))
                .andExpect(jsonPath("$[?(@.relatedTaskId == " + child + ")].relationshipType").value("RELATES_TO"));
        mvc.perform(get("/api/projects/{p}/tasks/{t}/links", project, child).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.relatedTaskId == " + first + ")].relationshipType").value("DUPLICATES"));

        assertThat(jdbc.queryForObject("SELECT relationship_type FROM project_task_links WHERE source_task_id=? AND target_task_id=?", String.class, child, first)).isEqualTo("DUPLICATES");
        assertThat(jdbc.queryForObject("SELECT source_task_id < target_task_id FROM project_task_links WHERE relationship_type='RELATES_TO' AND project_id=?", Boolean.class, project)).isTrue();
    }

    @Test
    void rejectsInvalidDuplicateCrossProjectGeneralAndUnsupportedLinks() throws Exception {
        long employee = userId("nora.ahmed@example.com");
        long project = project("Link validation"); long other = project("Other links");
        long first = task(project, employee, null, "First"); long second = task(project, employee, null, "Second");
        long foreign = task(other, employee, null, "Foreign"); long general = generalRequest(employee);
        Session admin = login("sara.saad@example.com");

        create(admin, project, first, second, "BLOCKS");
        performCreate(admin, project, first, second, "BLOCKS").andExpect(status().isConflict());
        performCreate(admin, project, second, first, "IS_BLOCKED_BY").andExpect(status().isConflict());
        performCreate(admin, project, first, first, "RELATES_TO").andExpect(status().isBadRequest());
        performCreate(admin, project, first, foreign, "RELATES_TO").andExpect(status().isNotFound());
        performCreate(admin, project, first, general, "RELATES_TO").andExpect(status().isNotFound());
        performCreate(admin, project, first, second, "CLONES").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_links WHERE project_id=?", Long.class, project)).isOne();
    }

    @Test
    void rejectsDirectAndIndirectBlockingCyclesWithoutRestrictingStatusChanges() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Blocking graph"); member(project, employee);
        long a = task(project, employee, null, "A"); long b = task(project, employee, null, "B"); long c = task(project, employee, a, "C");
        Session admin = login("sara.saad@example.com");
        create(admin, project, a, b, "BLOCKS");
        performCreate(admin, project, b, a, "BLOCKS").andExpect(status().isConflict());
        create(admin, project, b, c, "BLOCKS");
        performCreate(admin, project, c, a, "BLOCKS").andExpect(status().isConflict());

        long completed = statusId(project, "COMPLETED");
        mvc.perform(patch("/api/projects/{p}/tasks/{t}/status", project, c).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())
                        .contentType("application/json").content("{\"statusId\":" + completed + ",\"changeNote\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusId").value(completed));
    }

    @Test
    void enforcesMembershipCsrfAndArchivedReadOnlyWhileAllowingInternalReads() throws Exception {
        long nora = userId("nora.ahmed@example.com"); var reem = createActiveEmployee(jdbc, "Reem Khalid");
        long project = project("Link access"); member(project, nora);
        long first = task(project, nora, null, "First"); long second = task(project, nora, null, "Second");
        Session admin = login("sara.saad@example.com"); Session member = login("nora.ahmed@example.com"); Session outsider = login(reem.email());

        performEmployeeCreate(member, project, first, second, "RELATES_TO").andExpect(status().isCreated());
        performEmployeeCreate(outsider, project, first, second, "BLOCKS").andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/mine/{p}/tasks/{t}/links", project, first).session(member.session())
                .contentType("application/json").content(body(second, "BLOCKS"))).andExpect(status().isForbidden());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project); entityManager.clear();
        mvc.perform(get("/api/projects/mine/{p}/tasks/{t}/links", project, first).session(member.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        performEmployeeCreate(member, project, first, second, "BLOCKS").andExpect(status().isConflict());
        long linkId = jdbc.queryForObject("SELECT link_id FROM project_task_links WHERE project_id=?", Long.class, project);
        mvc.perform(delete("/api/projects/mine/{p}/tasks/{t}/links/{l}", project, first, linkId).session(member.session())
                        .cookie(member.csrf()).header("X-XSRF-TOKEN", member.csrf().getValue()))
                .andExpect(status().isConflict());
    }

    @Test
    void removingLinksAndDeletingTasksPreservesPeersAndUnrelatedLinks() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Link deletion"); member(project, employee);
        long parent = task(project, employee, null, "Parent"); long child = task(project, employee, parent, "Child");
        long peer = task(project, employee, null, "Peer"); long other = task(project, employee, null, "Other");
        Session admin = login("sara.saad@example.com");
        long childLink = create(admin, project, child, peer, "RELATES_TO");
        create(admin, project, peer, other, "BLOCKS");

        mvc.perform(delete("/api/projects/{p}/tasks/{t}/links/{l}", project, child, childLink).session(admin.session())
                        .cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?)", Long.class, child, peer)).isEqualTo(2);
        create(admin, project, child, peer, "RELATES_TO");

        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent).param("deleteSubtasks", "true")
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?)", Long.class, peer, other)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_links WHERE source_task_id=? AND target_task_id=?", Long.class, peer, other)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_task_links WHERE source_task_id=? OR target_task_id=?", Long.class, child, child)).isZero();
    }

    private long create(Session session, long project, long task, long related, String type) throws Exception {
        String json = performCreate(session, project, task, related, type).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
    }
    private org.springframework.test.web.servlet.ResultActions performCreate(Session session,long project,long task,long related,String type) throws Exception { return mvc.perform(post("/api/projects/{p}/tasks/{t}/links",project,task).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN",session.csrf().getValue()).contentType("application/json").content(body(related,type))); }
    private org.springframework.test.web.servlet.ResultActions performEmployeeCreate(Session session,long project,long task,long related,String type) throws Exception { return mvc.perform(post("/api/projects/mine/{p}/tasks/{t}/links",project,task).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN",session.csrf().getValue()).contentType("application/json").content(body(related,type))); }
    private String body(long related,String type){return "{\"relatedTaskId\":"+related+",\"relationshipType\":\""+type+"\"}";}
    private long project(String name){return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,'ACTIVE') RETURNING project_id",Long.class,name);}
    private long task(long project,long creator,Long parent,String title){long workType=workTypeId(jdbc,project,"Task");return jdbc.queryForObject("INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,project_id,assigned_to,parent_request_id) VALUES (?,'Details',?,'MEDIUM',?,?,?,?,?) RETURNING request_id",Long.class,title,workType,statusId(project,"NEW"),creator,project,creator,parent);}
    private long generalRequest(long creator){long type=jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1",Long.class);return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,priority,status,created_by) VALUES ('General','Details',?,'MEDIUM','NEW',?) RETURNING request_id",Long.class,type,creator);}
    private long statusId(long project,String key){return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",Long.class,project,key);}
    private void member(long project,long employee){jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)",project,employee);}
    private long userId(String email){return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email);}
    private Session login(String email)throws Exception{Cookie csrf=mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");assertThat(csrf).isNotNull();var result=mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn();return new Session((MockHttpSession)result.getRequest().getSession(false),csrf);}
    private record Session(MockHttpSession session,Cookie csrf){}
}
