package com.requestmanagement.backend.planning;

import com.requestmanagement.backend.automation.WorkflowIntegrationTestBase;
import com.requestmanagement.backend.usernotification.ProjectChangeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Project Roadmap drag moves a bar by N days: start and due shift together through the
 * existing dates endpoint (same authorization, validation and live update event).
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class RoadmapDragIntegrationTests extends WorkflowIntegrationTestBase {
    @Autowired ApplicationEvents events;

    @Test
    void membersAndAdminsShiftBothDatesByTheSameNumberOfDays() throws Exception {
        Session admin = adminSession(); Session employee = noraSession();
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        setDates(task, LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-13"));

        // Dragged 5 days later by a member (employee workspace).
        mvc.perform(write(patch("/api/projects/mine/" + project + "/tasks/" + task + "/dates"), employee)
                        .content("{\"startDate\":\"2026-11-15\",\"dueDate\":\"2026-11-18\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-11-15"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-18"));
        assertThat(events.stream(ProjectChangeEvent.class)
                .filter(event -> event.taskId() == task && event.change() == ProjectChangeEvent.ChangeType.DATES_CHANGED)).isNotEmpty();

        // Dragged 7 days earlier by an Admin; a due-date-only bar keeps having no start.
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/dates"), admin)
                        .content("{\"startDate\":\"2026-11-08\",\"dueDate\":\"2026-11-11\"}"))
                .andExpect(status().isOk());
        assertThat(dates(task)).isEqualTo("2026-11-08/2026-11-11");
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/dates"), admin)
                        .content("{\"startDate\":null,\"dueDate\":\"2026-11-20\"}"))
                .andExpect(status().isOk());
        assertThat(dates(task)).isEqualTo("null/2026-11-20");
    }

    @Test
    void invalidMovesAreRejectedSoTheClientRollsBack() throws Exception {
        Session admin = adminSession(); Session employee = noraSession();
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        setDates(task, LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-13"));

        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/dates"), admin)
                .content("{\"startDate\":\"2026-11-20\",\"dueDate\":\"2026-11-13\"}")).andExpect(status().isBadRequest());
        Session outsider = login(createOutsider());
        mvc.perform(write(patch("/api/projects/mine/" + project + "/tasks/" + task + "/dates"), outsider)
                .content("{\"startDate\":\"2026-11-11\",\"dueDate\":\"2026-11-14\"}")).andExpect(status().isNotFound());

        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/dates"), admin)
                .content("{\"startDate\":\"2026-11-11\",\"dueDate\":\"2026-11-14\"}")).andExpect(status().isConflict());
        mvc.perform(write(patch("/api/projects/mine/" + project + "/tasks/" + task + "/dates"), employee)
                .content("{\"startDate\":\"2026-11-11\",\"dueDate\":\"2026-11-14\"}")).andExpect(status().isConflict());
        assertThat(dates(task)).isEqualTo("2026-11-10/2026-11-13");
    }

    private String createOutsider() {
        var outsider = com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Roadmap Outsider");
        users.add(outsider.id());
        return outsider.email();
    }

    private void setDates(long task, LocalDate start, LocalDate due) {
        jdbc.update("UPDATE requests SET start_date=?, due_date=? WHERE request_id=?", start, due, task);
    }

    private String dates(long task) {
        return jdbc.queryForObject("SELECT COALESCE(start_date::text,'null') || '/' || COALESCE(due_date::text,'null') FROM requests WHERE request_id=?",
                String.class, task);
    }
}
