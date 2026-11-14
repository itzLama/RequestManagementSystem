package com.requestmanagement.backend.request;

import com.requestmanagement.backend.automation.WorkflowIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Archived Projects are read-only: no Task or subtask can be permanently deleted. */
@SpringBootTest
@AutoConfigureMockMvc
class ArchivedProjectTaskDeletionIntegrationTests extends WorkflowIntegrationTestBase {

    @Test
    void tasksAndSubtasksOfAnArchivedProjectCannotBeDeleted() throws Exception {
        Session admin = adminSession(); Session employee = noraSession();
        long parent = adminCreateTask(admin, nora, "MEDIUM", workType);
        long subtask = body(mvc.perform(write(post("/api/projects/" + project + "/tasks/" + parent + "/subtasks"), admin)
                        .content("{\"title\":\"Child\",\"description\":\"Details\",\"workTypeId\":" + workType
                                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + nora + ",\"statusId\":" + statusId("NEW") + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
        long single = adminCreateTask(admin, nora, "LOW", workType);
        mvc.perform(write(patch("/api/projects/" + project + "/archive"), admin)).andExpect(status().isOk());

        String tasks = "/api/projects/" + project + "/tasks/";
        mvc.perform(write(delete(tasks + single), admin)).andExpect(status().isConflict());
        mvc.perform(write(delete(tasks + subtask), admin)).andExpect(status().isConflict());
        mvc.perform(write(delete(tasks + parent + "?deleteSubtasks=true"), admin)).andExpect(status().isConflict());
        // Employees have no deletion endpoint at all (archived or not).
        mvc.perform(write(delete("/api/projects/mine/" + project + "/tasks/" + single), employee))
                .andExpect(status().is4xxClientError());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id IN (?,?,?)", Long.class,
                parent, subtask, single)).isEqualTo(3);
    }
}
