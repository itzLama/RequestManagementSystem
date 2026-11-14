package com.requestmanagement.backend.request;

import com.requestmanagement.backend.automation.WorkflowIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Internal Notes on Project Task Details (restored Admin panel): Admins add and read them through
 * the existing endpoints; members, Insights and Guest responses never contain them; adding one
 * notifies Admin participants only; archived Projects are read-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectTaskInternalNotesIntegrationTests extends WorkflowIntegrationTestBase {
    private static final String NOTE = "Admin-only note " + UUID.randomUUID();

    @Test
    void adminsReadAndAddNotesWhileMembersInsightsAndArchivedProjectsStayProtected() throws Exception {
        long secondAdmin = jdbc.queryForObject("""
                INSERT INTO users(full_name,email,password_hash,role,is_active)
                VALUES ('Notes Admin', ?, (SELECT password_hash FROM users WHERE email='sara.saad@example.com'), 'ADMIN', true)
                RETURNING user_id
                """, Long.class, "test-notes-admin-" + UUID.randomUUID() + "@example.com");
        users.add(secondAdmin);
        Session admin = adminSession(); Session member = noraSession();
        Session adminTwo = login(jdbc.queryForObject("SELECT email FROM users WHERE user_id=?", String.class, secondAdmin));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        String adminTask = "/api/projects/" + project + "/tasks/" + task;
        String memberTask = "/api/projects/mine/" + project + "/tasks/" + task;
        mvc.perform(write(post(adminTask + "/comments"), adminTwo).content("{\"text\":\"Second Admin joins\"}"))
                .andExpect(status().isCreated());

        mvc.perform(write(post(adminTask + "/internal-notes"), admin).content("{\"text\":\"" + NOTE + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value(NOTE));
        // Blank notes are rejected like comments.
        mvc.perform(write(post(adminTask + "/internal-notes"), admin).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Admin Task Details (what the panel renders) has the note, separate from the comments.
        mvc.perform(get(adminTask).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internalNotes.length()").value(1))
                .andExpect(jsonPath("$.internalNotes[0].text").value(NOTE))
                .andExpect(jsonPath("$.internalNotes[0].authorRole").value("ADMIN"))
                .andExpect(jsonPath("$.comments[?(@.text == '" + NOTE + "')]").isEmpty());

        // Members: no field, no text, and no way to add one.
        String memberBody = mvc.perform(get(memberTask).session(member.session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(memberBody).doesNotContain(NOTE).doesNotContain("internalNotes");
        mvc.perform(write(post(adminTask + "/internal-notes"), member).content("{\"text\":\"Sneaky\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(write(post(memberTask + "/internal-notes"), member).content("{\"text\":\"Sneaky\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=? AND is_internal", Long.class, task))
                .isEqualTo(1);

        // Insights activity never shows notes (Admin or member dashboard).
        assertThat(mvc.perform(get("/api/projects/" + project + "/dashboard").session(admin.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).doesNotContain(NOTE);
        assertThat(mvc.perform(get("/api/projects/mine/" + project + "/dashboard").session(member.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).doesNotContain(NOTE);

        // The existing Admin-only notification fires: the other Admin participant, never the Employee.
        assertThat(notifications(secondAdmin, task)).isEqualTo(1);
        assertThat(notifications(nora, task)).isZero();
        assertThat(notifications(adminId, task)).isZero(); // never the author

        // Archived Projects: notes stay readable, adding is rejected.
        mvc.perform(write(patch("/api/projects/" + project + "/archive"), admin)).andExpect(status().isOk());
        mvc.perform(get(adminTask).session(admin.session())).andExpect(jsonPath("$.internalNotes[0].text").value(NOTE));
        mvc.perform(write(post(adminTask + "/internal-notes"), admin).content("{\"text\":\"Too late\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE request_id=? AND is_internal", Long.class, task))
                .isEqualTo(1);
    }

    private long notifications(long recipient, long task) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_notifications WHERE recipient_id=? AND request_id=? AND notification_type='TASK_INTERNAL_NOTE'
                """, Long.class, recipient, task);
    }
}
