package com.requestmanagement.backend.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.requestmanagement.backend.project.ProjectWorkTypeTestSupport.workTypeId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ParentTaskDeletionRollbackIntegrationTests {
    @Autowired ProjectTaskService projectTaskService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean RequestRepository requestRepository;

    @Test
    void failureAfterSubtaskCleanupRollsBackTheWholeHierarchyDeletion() {
        long admin = userId("sara.saad@example.com");
        long employee = userId("nora.ahmed@example.com");
        long project = jdbc.queryForObject(
                "INSERT INTO projects(project_name,status) VALUES ('Subtask rollback test','ACTIVE') RETURNING project_id",
                Long.class);
        long status = jdbc.queryForObject(
                "SELECT status_id FROM project_status_columns WHERE project_id=? AND semantic_key='NEW'", Long.class, project);
        long workType = workTypeId(jdbc, project, "Task");
        long parent = task(project, employee, null, status, workType, "Rollback parent");
        long child = task(project, employee, parent, status, workType, "Rollback child");

        try {
            doNothing().doThrow(new DataIntegrityViolationException("Simulated final delete failure"))
                    .when(requestRepository).flush();

            assertThatThrownBy(() -> projectTaskService.permanentlyDeleteAdminTask(project, parent, admin, true))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(countRequest(parent)).isOne();
            assertThat(countRequest(child)).isOne();
        } finally {
            reset(requestRepository);
            projectTaskService.permanentlyDeleteAdminTask(project, parent, admin, true);
            jdbc.update("DELETE FROM project_status_columns WHERE project_id=?", project);
            jdbc.update("DELETE FROM project_work_types WHERE project_id=?", project);
            jdbc.update("DELETE FROM projects WHERE project_id=?", project);
        }
    }

    private long task(long project, long creator, Long parent, long status, long workType, String title) {
        return jdbc.queryForObject("""
                INSERT INTO requests(title,description,project_work_type_id,priority,project_status_id,
                                     created_by,project_id,assigned_to,parent_request_id)
                VALUES (?,'Details',?,'MEDIUM',?,?,?,?,?) RETURNING request_id
                """, Long.class, title, workType, status, creator, project, creator, parent);
    }

    private long countRequest(long requestId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, requestId);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email=?", Long.class, email);
    }
}
