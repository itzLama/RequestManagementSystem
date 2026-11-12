package com.requestmanagement.backend.request;

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
import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectSubtaskIntegrationTests {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired EntityManager entityManager;

    @Test
    void adminCreatesSubtasksAndBoardRemainsRootOnlyWithDynamicProgress() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Subtask progress", "ACTIVE"); member(project, employee);
        long parent = task(project, employee, null, statusId(project, "NEW"), "Parent"); Session admin = login("sara.saad@example.com");
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtaskProgress.total").value(0));
        long customCompleted = jdbc.queryForObject("INSERT INTO project_status_columns(project_id,status_name,display_order,counts_as_completed) VALUES (?,'Verified',5,true) RETURNING status_id", Long.class, project);
        long first = createSubtask(admin, project, parent, employee, statusId(project, "COMPLETED"), "Completed child");
        long second = createSubtask(admin, project, parent, employee, customCompleted, "Verified child");
        createSubtask(admin, project, parent, employee, statusId(project, "NEW"), "Open child");

        mvc.perform(get("/api/projects/{p}/tasks/board", project).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(parent));
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtasks.length()").value(3))
                .andExpect(jsonPath("$.subtaskProgress.completed").value(2))
                .andExpect(jsonPath("$.subtaskProgress.total").value(3))
                .andExpect(jsonPath("$.subtaskProgress.percentage").value(67))
                .andExpect(jsonPath("$.statusSemantic").value("NEW"));
        assertThat(first).isNotEqualTo(second);

        jdbc.update("UPDATE project_status_columns SET counts_as_completed=false WHERE project_id=? AND counts_as_completed=true", project);
        entityManager.clear();
        mvc.perform(get("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtaskProgress.completed").value(0))
                .andExpect(jsonPath("$.subtaskProgress.percentage").value(0));
    }

    @Test
    void hierarchyAndProjectBoundariesAreEnforced() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Hierarchy A", "ACTIVE"); long other = project("Hierarchy B", "ACTIVE"); member(project, employee);
        long parent = task(project, employee, null, statusId(project, "NEW"), "Parent"); Session admin = login("sara.saad@example.com");
        long child = createSubtask(admin, project, parent, employee, statusId(project, "NEW"), "Child");
        mvc.perform(post("/api/projects/{p}/tasks/{t}/subtasks", project, child).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content(input(project, employee, statusId(project, "NEW"), "Nested")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/projects/{p}/tasks/{t}/subtasks", other, parent).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content(input(other, employee, statusId(other, "NEW"), "Cross")))
                .andExpect(status().isNotFound());
        long general = generalRequest(employee);
        mvc.perform(post("/api/projects/{p}/tasks/{t}/subtasks", project, general).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()).contentType("application/json").content(input(project, employee, statusId(project, "NEW"), "General")))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeRequiresMembershipAndArchivedProjectIsReadOnly() throws Exception {
        long nora = userId("nora.ahmed@example.com"); long project = project("Subtask access", "ACTIVE"); long parent = task(project, nora, null, statusId(project, "NEW"), "Parent"); Session employee = login("nora.ahmed@example.com");
        mvc.perform(post("/api/projects/mine/{p}/tasks/{t}/subtasks", project, parent).session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json").content(input(project, nora, statusId(project, "NEW"), "Denied")))
                .andExpect(status().isNotFound());
        member(project, nora);
        createEmployeeSubtask(employee, project, parent, nora, "Allowed");
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project); entityManager.clear();
        mvc.perform(get("/api/projects/mine/{p}/tasks/{t}", project, parent).session(employee.session())).andExpect(status().isOk());
        mvc.perform(post("/api/projects/mine/{p}/tasks/{t}/subtasks", project, parent).session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()).contentType("application/json").content(input(project, nora, statusId(project, "NEW"), "Archived")))
                .andExpect(status().isConflict());
    }

    @Test
    void parentDeletionRequiresExplicitAuthorizationAndIndividualSubtaskDeletionPreservesParent() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Subtask deletion", "ACTIVE"); member(project, employee);
        long parent = task(project, employee, null, statusId(project, "NEW"), "Parent"); Session admin = login("sara.saad@example.com");
        long child = createSubtask(admin, project, parent, employee, statusId(project, "NEW"), "Child");
        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())).andExpect(status().isConflict());
        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, child).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue())).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, parent)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, child)).isZero();
    }

    @Test
    void adminExplicitlyDeletesParentSubtasksAndOwnedHistoryButPreservesUnrelatedTask() throws Exception {
        long employee = userId("nora.ahmed@example.com"); long project = project("Delete hierarchy", "ACTIVE"); member(project, employee);
        long parent = task(project, employee, null, statusId(project, "NEW"), "Parent to delete");
        long unrelated = task(project, employee, null, statusId(project, "NEW"), "Unrelated survivor");
        Session admin = login("sara.saad@example.com");
        long first = createSubtask(admin, project, parent, employee, statusId(project, "NEW"), "First child");
        long second = createSubtask(admin, project, parent, employee, statusId(project, "IN_PROGRESS"), "Second child");
        long adminId = userId("sara.saad@example.com");
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text) VALUES (?,?,?)", first, adminId, "Child comment");
        jdbc.update("INSERT INTO status_history(request_id,old_project_status_id,new_project_status_id,changed_by) VALUES (?,?,?,?)",
                second, statusId(project, "NEW"), statusId(project, "IN_PROGRESS"), adminId);
        jdbc.update("INSERT INTO comments(request_id,user_id,comment_text) VALUES (?,?,?)", unrelated, adminId, "Keep me");

        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent)
                        .param("deleteSubtasks", "true").session(admin.session()).cookie(admin.csrf())
                        .header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?,?)", Long.class, parent, first, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Long.class, first)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM status_history WHERE request_id=?", Long.class, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, unrelated)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=?", Long.class, unrelated)).isOne();
    }

    @Test
    void deleteAllRequiresAdminActiveProjectValidTaskAndCsrf() throws Exception {
        long employeeId = userId("nora.ahmed@example.com"); long project = project("Delete access", "ACTIVE"); member(project, employeeId);
        long parent = task(project, employeeId, null, statusId(project, "NEW"), "Protected parent");
        Session admin = login("sara.saad@example.com"); Session employee = login("nora.ahmed@example.com");
        createSubtask(admin, project, parent, employeeId, statusId(project, "NEW"), "Protected child");

        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent).param("deleteSubtasks", "true")
                        .session(employee.session()).cookie(employee.csrf()).header("X-XSRF-TOKEN", employee.csrf().getValue()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent).param("deleteSubtasks", "true").session(admin.session()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, Long.MAX_VALUE).param("deleteSubtasks", "true")
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project); entityManager.clear();
        mvc.perform(delete("/api/projects/{p}/tasks/{t}", project, parent).param("deleteSubtasks", "true")
                        .session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN", admin.csrf().getValue()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=? OR parent_request_id=?", Long.class, parent, parent)).isEqualTo(2);
    }

    private long createSubtask(Session admin,long project,long parent,long assignee,long status,String title) throws Exception { MvcResult result=mvc.perform(post("/api/projects/{p}/tasks/{t}/subtasks",project,parent).session(admin.session()).cookie(admin.csrf()).header("X-XSRF-TOKEN",admin.csrf().getValue()).contentType("application/json").content(input(project,assignee,status,title))).andExpect(status().isCreated()).andReturn();return ((Number)com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.id")).longValue(); }
    private void createEmployeeSubtask(Session session,long project,long parent,long assignee,String title) throws Exception { mvc.perform(post("/api/projects/mine/{p}/tasks/{t}/subtasks",project,parent).session(session.session()).cookie(session.csrf()).header("X-XSRF-TOKEN",session.csrf().getValue()).contentType("application/json").content(input(project,assignee,statusId(project,"NEW"),title))).andExpect(status().isCreated()); }
    private String input(long project,long assignee,long status,String title){long workType=workTypeId(jdbc,project,"Task");return "{\"title\":\""+title+"\",\"description\":\"Details\",\"workTypeId\":"+workType+",\"priority\":\"MEDIUM\",\"assignedToId\":"+assignee+",\"statusId\":"+status+"}";}
    private long project(String name,String state){return jdbc.queryForObject("INSERT INTO projects(project_name,status) VALUES (?,?) RETURNING project_id",Long.class,name,state);}
    private long task(long project,long creator,Long parent,long status,String title){long workType=workTypeId(jdbc,project,"Task");return jdbc.queryForObject("INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,created_by,project_id,assigned_to,parent_request_id) VALUES (?,'Details',?,'MEDIUM',?,?,?,?,?) RETURNING request_id",Long.class,title,workType,status,creator,project,creator,parent);}
    private long generalRequest(long creator){long type=jdbc.queryForObject("SELECT type_id FROM request_types ORDER BY type_id LIMIT 1",Long.class);return jdbc.queryForObject("INSERT INTO requests(title,description,type_id,priority,status,created_by) VALUES ('General','Details',?,'MEDIUM','NEW',?) RETURNING request_id",Long.class,type,creator);}
    private long statusId(long project,String key){return jdbc.queryForObject("SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key=?",Long.class,project,key);}
    private void member(long project,long employee){jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)",project,employee);}
    private long userId(String email){return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email);}
    private Session login(String email)throws Exception{Cookie csrf=mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");assertThat(csrf).isNotNull();MvcResult result=mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Password123\"}")).andExpect(status().isOk()).andReturn();return new Session((MockHttpSession)result.getRequest().getSession(false),csrf);}
    private record Session(MockHttpSession session,Cookie csrf){}
}
