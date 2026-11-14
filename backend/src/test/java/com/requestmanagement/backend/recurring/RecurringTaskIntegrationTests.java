package com.requestmanagement.backend.recurring;

import com.requestmanagement.backend.automation.WorkflowIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecurringTaskIntegrationTests extends WorkflowIntegrationTestBase {
    @Autowired RecurringTaskRunner runner;

    @Test
    void runNowCreatesTodaysOccurrenceThroughTheNormalCreationPathExactlyOnce() throws Exception {
        Session admin = adminSession();
        long template = template(3);
        LocalDate today = runner.today();
        JsonNode rule = body(mvc.perform(write(post(rules()), admin).content(ruleBody(template, "DAILY", null, null,
                today.toString(), null, nora, true))).andExpect(status().isCreated()));
        long ruleId = rule.get("id").asLong();
        assertThat(rule.get("nextRunDate").asString()).isEqualTo(today.toString());
        assertThat(rule.get("ownerName").asString()).isEqualTo("Sara Saad");

        JsonNode run = body(mvc.perform(write(post(rules() + "/" + ruleId + "/run"), admin)).andExpect(status().isOk()));
        assertThat(run.get("result").asString()).isEqualTo("CREATED");
        long task = run.get("taskId").asLong();
        Map<String, Object> created = jdbc.queryForMap("""
                SELECT r.title, r.priority, r.created_by, r.assigned_to, r.start_date, r.due_date, r.project_work_type_id,
                       r.description, s.semantic_key
                  FROM requests r JOIN project_status_columns s ON s.status_id = r.project_status_id
                 WHERE r.request_id = ?
                """, task);
        assertThat(created.get("title")).isEqualTo("Weekly server check");
        assertThat(created.get("priority")).isEqualTo("HIGH");
        assertThat(created.get("semantic_key")).isEqualTo("NEW");
        assertThat(((Number) created.get("created_by")).longValue()).isEqualTo(adminId);
        assertThat(((Number) created.get("assigned_to")).longValue()).isEqualTo(nora);
        assertThat(((Number) created.get("project_work_type_id")).longValue()).isEqualTo(workType);
        assertThat(created.get("start_date").toString()).isEqualTo(today.toString());
        assertThat(created.get("due_date").toString()).isEqualTo(today.plusDays(3).toString());
        assertThat(created.get("description")).isEqualTo("Weekly checklist");
        // Existing notifications fire normally: the assignee is told by the rule's owner.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_notifications
                 WHERE request_id=? AND recipient_id=? AND notification_type='TASK_ASSIGNED' AND actor_id=?
                """, Long.class, task, nora, adminId)).isEqualTo(1);

        // Running again (button or job) never creates a second Task for the same day.
        JsonNode again = body(mvc.perform(write(post(rules() + "/" + ruleId + "/run"), admin)).andExpect(status().isOk()));
        assertThat(again.get("result").asString()).isEqualTo("ALREADY_EXISTS");
        assertThat(runner.runDue()).isZero();
        assertThat(tasksOfRule(ruleId)).isEqualTo(1);
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].lastRunResult").value("ALREADY_EXISTS"))
                .andExpect(jsonPath("$[0].nextRunDate").value(today.plusDays(1).toString()));
    }

    @Test
    void scheduledRunIsIdempotentEvenWhenTwoRunsOverlap() throws Exception {
        long template = template(null);
        long ruleId = insertRule(template, "DAILY", null, null, runner.today(), null, null, runner.today());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> job = () -> runner.runDue();
            List<Future<Integer>> runs = pool.invokeAll(List.of(job, job));
            int created = 0;
            for (Future<Integer> run : runs) created += run.get();
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(runner.runDue()).isZero();
        assertThat(tasksOfRule(ruleId)).isEqualTo(1);
        // A rule without a default assignee creates an unassigned Task (the Admin or an automation rule assigns it).
        Long assignee = jdbc.queryForObject("""
                SELECT r.assigned_to FROM project_recurring_task_occurrences o JOIN requests r ON r.request_id = o.request_id
                 WHERE o.rule_id = ?
                """, Long.class, ruleId);
        assertThat(assignee).isNull();
    }

    @Test
    void serverDowntimeCreatesOnlyTheLatestMissedOccurrenceAndNothingBeforeTheRuleWasEffective() throws Exception {
        long template = template(null);
        LocalDate today = runner.today();
        // Weekly on a weekday that is not today: the latest occurrence is in the past week.
        int weekday = today.minusDays(2).getDayOfWeek().getValue();
        LocalDate longAgo = today.minusDays(60);
        long missed = insertRule(template, "WEEKLY", weekday, null, longAgo, null, nora, longAgo);
        // Created today: the past occurrence must not be created retroactively.
        long fresh = insertRule(template, "WEEKLY", weekday, null, longAgo, null, nora, today);
        // Ended before today: its last date is long gone, still at most one is created.
        long ended = insertRule(template, "DAILY", null, null, longAgo, today.minusDays(30), nora, longAgo);

        runner.runDue();
        runner.runDue();
        assertThat(jdbc.queryForList("SELECT occurrence_date FROM project_recurring_task_occurrences WHERE rule_id=?",
                LocalDate.class, missed)).containsExactly(today.minusDays(2));
        assertThat(tasksOfRule(fresh)).isZero();
        assertThat(jdbc.queryForList("SELECT occurrence_date FROM project_recurring_task_occurrences WHERE rule_id=?",
                LocalDate.class, ended)).containsExactly(today.minusDays(30));
    }

    @Test
    void rulesAreSkippedWithAWarningForRemovedAssigneesRetiredWorkTypesAndArchivedProjects() throws Exception {
        Session admin = adminSession();
        long template = template(null);
        long ruleId = insertRule(template, "DAILY", null, null, runner.today(), null, peer.id(), runner.today());

        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, peer.id());
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].warnings", hasItem("ASSIGNEE_NOT_MEMBER")));
        JsonNode skipped = body(mvc.perform(write(post(rules() + "/" + ruleId + "/run"), admin)).andExpect(status().isOk()));
        assertThat(skipped.get("result").asString()).isEqualTo("SKIPPED_ASSIGNEE_NOT_MEMBER");
        assertThat(runner.runDue()).isZero();
        assertThat(tasksOfRule(ruleId)).isZero();

        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, peer.id());
        jdbc.update("UPDATE project_work_types SET is_active=false WHERE work_type_id=?", workType);
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].warnings", hasItem("RETIRED_WORK_TYPE")));
        assertThat(runner.runDue()).isZero();
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].lastRunResult").value("SKIPPED_RETIRED_WORK_TYPE"));

        jdbc.update("UPDATE project_work_types SET is_active=true WHERE work_type_id=?", workType);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].warnings", hasItem("ARCHIVED_PROJECT")))
                .andExpect(jsonPath("$[0].nextRunDate").doesNotExist());
        mvc.perform(write(post(rules() + "/" + ruleId + "/run"), admin)).andExpect(status().isConflict());
        mvc.perform(write(put(rules() + "/" + ruleId), admin).content(ruleBody(template, "DAILY", null, null,
                runner.today().toString(), null, null, true))).andExpect(status().isConflict());
        assertThat(runner.runDue()).isZero();
        assertThat(tasksOfRule(ruleId)).isZero();

        jdbc.update("UPDATE projects SET status='ACTIVE' WHERE project_id=?", project);
        assertThat(runner.runDue()).isEqualTo(1);
    }

    @Test
    void onlyAdministratorsManageRulesAndInputIsValidated() throws Exception {
        Session admin = adminSession(); Session employee = noraSession();
        long template = template(null);
        String valid = ruleBody(template, "WEEKLY", 3, null, runner.today().toString(), null, nora, true);
        mvc.perform(get(rules()).session(employee.session())).andExpect(status().isForbidden());
        mvc.perform(write(post(rules()), employee).content(valid)).andExpect(status().isForbidden());

        mvc.perform(write(post(rules()), admin).content(ruleBody(template, "WEEKLY", null, null,
                runner.today().toString(), null, null, true))).andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(ruleBody(template, "MONTHLY", null, 32,
                runner.today().toString(), null, null, true))).andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(ruleBody(template, "DAILY", null, null,
                runner.today().toString(), runner.today().minusDays(1).toString(), null, true))).andExpect(status().isBadRequest());
        long outsider = com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Outsider").id();
        users.add(outsider);
        mvc.perform(write(post(rules()), admin).content(ruleBody(template, "DAILY", null, null,
                runner.today().toString(), null, outsider, true))).andExpect(status().isBadRequest());
        long otherProject = newProject("Other");
        mvc.perform(write(post("/api/projects/" + otherProject + "/recurring-tasks"), admin).content(valid))
                .andExpect(status().isBadRequest());

        JsonNode rule = body(mvc.perform(write(post(rules()), admin).content(valid)).andExpect(status().isCreated()));
        long ruleId = rule.get("id").asLong();
        assertThat(rule.get("weekday").asInt()).isEqualTo(3);
        assertThat(rule.path("monthDay").isMissingNode() || rule.path("monthDay").isNull()).isTrue();
        mvc.perform(write(post(rules() + "/" + ruleId + "/run"), employee)).andExpect(status().isForbidden());
        mvc.perform(write(delete(rules() + "/" + ruleId), employee)).andExpect(status().isForbidden());

        // A template used by a rule cannot be deleted silently.
        mvc.perform(write(delete("/api/projects/" + project + "/task-templates/" + template), admin))
                .andExpect(status().isConflict());
        mvc.perform(write(delete(rules() + "/" + ruleId), admin)).andExpect(status().isNoContent());
        mvc.perform(write(delete("/api/projects/" + project + "/task-templates/" + template), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void pausingAndResumingARuleDoesNotBackfillThePausedDays() throws Exception {
        Session admin = adminSession();
        long template = template(null);
        LocalDate today = runner.today();
        long ruleId = insertRule(template, "DAILY", null, null, today.minusDays(10), null, nora, today.minusDays(10));
        jdbc.update("UPDATE project_recurring_task_rules SET is_active=false WHERE rule_id=?", ruleId);
        assertThat(runner.runDue()).isZero();
        mvc.perform(write(put(rules() + "/" + ruleId), admin).content(ruleBody(template, "DAILY", null, null,
                today.minusDays(10).toString(), null, nora, true))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT effective_from FROM project_recurring_task_rules WHERE rule_id=?",
                LocalDate.class, ruleId)).isEqualTo(today);
        assertThat(runner.runDue()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT occurrence_date FROM project_recurring_task_occurrences WHERE rule_id=?",
                LocalDate.class, ruleId)).containsExactly(today);
    }

    private String rules() {
        return "/api/projects/" + project + "/recurring-tasks";
    }

    private long tasksOfRule(long ruleId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_recurring_task_occurrences o JOIN requests r ON r.request_id = o.request_id
                 WHERE o.rule_id = ?
                """, Long.class, ruleId);
    }

    private long insertRule(long template, String frequency, Integer weekday, Integer monthDay, LocalDate start,
                            LocalDate end, Long assignee, LocalDate effectiveFrom) {
        return jdbc.queryForObject("""
                INSERT INTO project_recurring_task_rules(project_id,template_id,frequency,weekday,month_day,start_date,
                        end_date,assignee_id,is_active,effective_from,owner_id)
                VALUES (?,?,?,?,?,?,?,?,true,?,?) RETURNING rule_id
                """, Long.class, project, template, frequency, weekday, monthDay, start, end, assignee, effectiveFrom, adminId);
    }

    private static String ruleBody(long template, String frequency, Integer weekday, Integer monthDay, String start,
                                   String end, Long assignee, boolean active) {
        return "{\"templateId\":" + template + ",\"frequency\":\"" + frequency + "\",\"weekday\":" + weekday
                + ",\"monthDay\":" + monthDay + ",\"startDate\":\"" + start + "\",\"endDate\":"
                + (end == null ? "null" : "\"" + end + "\"") + ",\"assigneeId\":" + assignee + ",\"active\":" + active + "}";
    }
}
