package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.recurring.RecurringTaskRunner;
import com.requestmanagement.backend.usernotification.TaskNotificationPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AutomationIntegrationTests extends WorkflowIntegrationTestBase {
    @MockitoSpyBean TaskNotificationPublisher notificationPublisher;
    @Autowired AutomationEngine engine;
    @Autowired RecurringTaskRunner recurringRunner;

    @AfterEach
    void resetSpies() {
        reset(notificationPublisher);
    }

    @Test
    void taskCreatedTriggerAssignsTheTaskAndTheChangeIsAttributedToTheRule() throws Exception {
        Session admin = adminSession();
        long rule = createRule(admin, rule("Auto-assign", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);

        assertThat(assigneeOf(task)).isEqualTo(peer.id());
        Map<String, Object> notification = jdbc.queryForMap("""
                SELECT actor_id, automation_rule_id, automation_rule_name, automation_rule_name_ar FROM user_notifications
                 WHERE request_id=? AND recipient_id=? AND notification_type='TASK_ASSIGNED'
                """, task, peer.id());
        assertThat(notification.get("actor_id")).isNull();
        assertThat(((Number) notification.get("automation_rule_id")).longValue()).isEqualTo(rule);
        assertThat(notification.get("automation_rule_name")).isEqualTo("Auto-assign");
        assertThat(notification.get("automation_rule_name_ar")).isEqualTo("Auto-assign AR");
        Session peerSession = login(peer.email());
        mvc.perform(get("/api/notifications").session(peerSession.session()))
                .andExpect(jsonPath("$.items[0].automationRuleName").value("Auto-assign"))
                .andExpect(jsonPath("$.items[0].actorName").doesNotExist());
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].lastRunResult").value("APPLIED"))
                .andExpect(jsonPath("$[0].lastRunTaskId").value(task));
    }

    @Test
    void statusChangedTriggerMovesTheTaskAndHistoryShowsTheRuleNotAPerson() throws Exception {
        Session admin = adminSession();
        long completed = statusId("COMPLETED");
        createRule(admin, rule("Close waiting", "STATUS_CHANGED", statusId("WAITING_USER"), null, null, null, completed, List.of()));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);

        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/status"), admin)
                .content("{\"statusId\":" + statusId("IN_PROGRESS") + "}")).andExpect(status().isOk());
        assertThat(statusOf(task)).isEqualTo(statusId("IN_PROGRESS"));
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/status"), admin)
                .content("{\"statusId\":" + statusId("WAITING_USER") + "}")).andExpect(status().isOk());
        assertThat(statusOf(task)).isEqualTo(completed);

        JsonNode timeline = body(mvc.perform(get("/api/projects/" + project + "/tasks/" + task).session(admin.session()))
                .andExpect(status().isOk())).get("timeline");
        JsonNode byRule = null; JsonNode byPerson = null;
        for (JsonNode entry : timeline) {
            if (entry.get("newProjectStatusId").asLong() == completed) byRule = entry;
            if (entry.get("newProjectStatusId").asLong() == statusId("WAITING_USER")) byPerson = entry;
        }
        assertThat(byRule).isNotNull(); assertThat(byPerson).isNotNull();
        assertThat(byRule.get("automationRuleName").asString()).isEqualTo("Close waiting");
        assertThat(byRule.get("automationRuleNameAr").asString()).isEqualTo("Close waiting AR");
        assertThat(byRule.path("changedByName").isMissingNode() || byRule.path("changedByName").isNull()).isTrue();
        assertThat(byPerson.get("changedByName").asString()).isEqualTo("Sara Saad");
        assertThat(byPerson.path("automationRuleName").isMissingNode() || byPerson.path("automationRuleName").isNull()).isTrue();
        // The assignee's status notification also names the rule.
        assertThat(jdbc.queryForObject("""
                SELECT automation_rule_name FROM user_notifications
                 WHERE request_id=? AND recipient_id=? AND notification_type='TASK_STATUS_CHANGED' AND project_status_id=?
                """, String.class, task, nora, completed)).isEqualTo("Close waiting");
    }

    @Test
    void assigneeChangedTriggerNotifiesChosenMembersWithTheNewNotificationType() throws Exception {
        Session admin = adminSession();
        createRule(admin, rule("Tell the lead", "ASSIGNEE_CHANGED", null, null, null, null, null, List.of(nora)));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(count("AUTOMATION_NOTIFY", task, nora)).isZero();

        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/assignee"), admin)
                .content("{\"assignedToId\":" + peer.id() + "}")).andExpect(status().isOk());
        assertThat(count("AUTOMATION_NOTIFY", task, nora)).isEqualTo(1);
        Session noraSession = noraSession();
        mvc.perform(get("/api/notifications").session(noraSession.session()))
                .andExpect(jsonPath("$.items[0].type").value("AUTOMATION_NOTIFY"))
                .andExpect(jsonPath("$.items[0].automationRuleName").value("Tell the lead"));
    }

    @Test
    void conditionsOnWorkTypeAndPriorityLimitWhichTasksAreAffected() throws Exception {
        Session admin = adminSession();
        createRule(admin, rule("High bugs to peer", "TASK_CREATED", null, otherWorkType, "HIGH", peer.id(), null, List.of()));
        long wrongType = adminCreateTask(admin, nora, "HIGH", workType);
        long wrongPriority = adminCreateTask(admin, nora, "LOW", otherWorkType);
        long match = adminCreateTask(admin, nora, "HIGH", otherWorkType);
        assertThat(assigneeOf(wrongType)).isEqualTo(nora);
        assertThat(assigneeOf(wrongPriority)).isEqualTo(nora);
        assertThat(assigneeOf(match)).isEqualTo(peer.id());
    }

    @Test
    void changesMadeByAutomationNeverTriggerFurtherAutomation() throws Exception {
        Session admin = adminSession();
        long inProgress = statusId("IN_PROGRESS");
        createRule(admin, rule("Start new work", "TASK_CREATED", null, null, null, peer.id(), inProgress, List.of()));
        createRule(admin, rule("Finish started work", "STATUS_CHANGED", inProgress, null, null, null, statusId("COMPLETED"), List.of()));
        long notifyRule = createRule(admin, rule("Watch reassignments", "ASSIGNEE_CHANGED", null, null, null, null, null, List.of(nora)));

        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(statusOf(task)).isEqualTo(inProgress);
        assertThat(assigneeOf(task)).isEqualTo(peer.id());
        assertThat(count("AUTOMATION_NOTIFY", task, nora)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_run_result FROM project_automation_rules WHERE rule_id=?",
                String.class, notifyRule)).isNull();

        // The same rules still react to people's changes.
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/assignee"), admin)
                .content("{\"assignedToId\":" + nora + "}")).andExpect(status().isOk());
        assertThat(count("AUTOMATION_NOTIFY", task, nora)).isEqualTo(1);
    }

    @Test
    void aFailingRuleIsLoggedAndNeverRollsBackTheOriginalChangeOrOtherRules() throws Exception {
        Session admin = adminSession();
        long failing = createRule(admin, rule("Broken notify", "TASK_CREATED", null, null, null, null, null, List.of(peer.id())));
        long working = createRule(admin, rule("Move along", "TASK_CREATED", null, null, null, null, statusId("IN_PROGRESS"), List.of()));
        doThrow(new IllegalStateException("simulated failure")).when(notificationPublisher).automationNotify(any(), any());

        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM requests WHERE request_id=?", Long.class, task)).isEqualTo(1);
        assertThat(statusOf(task)).isEqualTo(statusId("IN_PROGRESS"));
        assertThat(lastResult(failing)).isEqualTo("FAILED");
        assertThat(lastResult(working)).isEqualTo("APPLIED");
    }

    @Test
    void invalidTargetsAreReportedAndTheRuleIsSkipped() throws Exception {
        Session admin = adminSession();
        long rule = createRule(admin, rule("Assign to peer", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, peer.id());
        mvc.perform(get(rules()).session(admin.session()))
                .andExpect(jsonPath("$[0].warnings", hasItem("ASSIGNEE_NOT_MEMBER")));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(assigneeOf(task)).isEqualTo(nora);
        assertThat(lastResult(rule)).isEqualTo("SKIPPED_INVALID_TARGET");

        long retiring = jdbc.queryForObject("""
                INSERT INTO project_status_columns(project_id,status_name,display_order) VALUES (?,'Review',5) RETURNING status_id
                """, Long.class, project);
        long moveRule = createRule(admin, rule("Send to review", "TASK_CREATED", null, null, null, null, retiring, List.of()));
        jdbc.update("UPDATE project_status_columns SET is_active=false, display_order=99 WHERE status_id=?", retiring);
        mvc.perform(get(rules() + "").session(admin.session()))
                .andExpect(jsonPath("$[1].warnings", hasItem("ACTION_STATUS_RETIRED")));
        long another = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(statusOf(another)).isEqualTo(statusId("NEW"));
        assertThat(lastResult(moveRule)).isEqualTo("SKIPPED_INVALID_TARGET");
    }

    @Test
    void disabledRulesAndArchivedProjectsNeverRunAutomation() throws Exception {
        Session admin = adminSession();
        long rule = createRule(admin, rule("Paused", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        mvc.perform(write(put(rules() + "/" + rule), admin)
                .content(rule("Paused", "TASK_CREATED", null, null, null, peer.id(), null, List.of()).replace("\"active\":true", "\"active\":false")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        long task = adminCreateTask(admin, nora, "MEDIUM", workType);
        assertThat(assigneeOf(task)).isEqualTo(nora);

        jdbc.update("UPDATE project_automation_rules SET is_active=true WHERE rule_id=?", rule);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        engine.run(new AutomationTriggerEvent(project, task, AutomationTrigger.TASK_CREATED, null));
        assertThat(assigneeOf(task)).isEqualTo(nora);
        assertThat(lastResult(rule)).isNull();
        mvc.perform(get(rules()).session(admin.session())).andExpect(jsonPath("$[0].warnings", hasItem("ARCHIVED_PROJECT")));
        mvc.perform(write(post(rules()), admin).content(rule("New", "TASK_CREATED", null, null, null, peer.id(), null, List.of())))
                .andExpect(status().isConflict());
    }

    @Test
    void recurringTaskCreationCountsAsTaskCreated() throws Exception {
        Session admin = adminSession();
        createRule(admin, rule("Assign recurring", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        long template = template(null);
        long recurring = jdbc.queryForObject("""
                INSERT INTO project_recurring_task_rules(project_id,template_id,frequency,start_date,is_active,effective_from,owner_id)
                VALUES (?,?,'DAILY',?,true,?,?) RETURNING rule_id
                """, Long.class, project, template, recurringRunner.today(), recurringRunner.today(), adminId);
        JsonNode run = body(mvc.perform(write(post("/api/projects/" + project + "/recurring-tasks/" + recurring + "/run"), admin))
                .andExpect(status().isOk()));
        assertThat(run.get("result").asString()).isEqualTo("CREATED");
        assertThat(assigneeOf(run.get("taskId").asLong())).isEqualTo(peer.id());
    }

    @Test
    void guestSubmissionsCountAsTaskCreated() throws Exception {
        Session admin = adminSession();
        createRule(admin, rule("Assign guest work", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        String body = mvc.perform(post("/api/guest/requests/projects/" + project).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                        .content("{\"title\":\"Guest issue\",\"description\":\"Help\",\"guestName\":\"Guest\","
                                + "\"guestEmail\":\"guest-automation@example.com\",\"workTypeId\":" + workType
                                + ",\"priority\":\"MEDIUM\",\"answers\":[]}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        long task = json.readTree(body).get("requestId").asLong();
        assertThat(assigneeOf(task)).isEqualTo(peer.id());
    }

    @Test
    void onlyAdministratorsManageRulesAndTargetsAreValidated() throws Exception {
        Session admin = adminSession(); Session employee = noraSession();
        String valid = rule("Valid", "TASK_CREATED", null, null, null, peer.id(), null, List.of());
        mvc.perform(get(rules()).session(employee.session())).andExpect(status().isForbidden());
        mvc.perform(write(post(rules()), employee).content(valid)).andExpect(status().isForbidden());

        long outsider = com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Outsider").id();
        users.add(outsider);
        long otherProject = newProject("Other");
        mvc.perform(write(post(rules()), admin).content(rule("No action", "TASK_CREATED", null, null, null, null, null, List.of())))
                .andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(rule("Outsider", "TASK_CREATED", null, null, null, outsider, null, List.of())))
                .andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(rule("Notify outsider", "TASK_CREATED", null, null, null, null, null, List.of(outsider))))
                .andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(rule("Foreign status", "TASK_CREATED", null, null, null, null,
                statusId(otherProject, "COMPLETED"), List.of()))).andExpect(status().isBadRequest());
        mvc.perform(write(post(rules()), admin).content(rule("Missing trigger status", "STATUS_CHANGED", null, null, null, peer.id(), null, List.of())))
                .andExpect(status().isBadRequest());

        long id = createRule(admin, valid);
        mvc.perform(write(delete(rules() + "/" + id), employee)).andExpect(status().isForbidden());
        mvc.perform(write(delete(rules() + "/" + id), admin)).andExpect(status().isNoContent());
    }

    @Test
    void createTaskPreviewListsOnlyRunnableTaskCreatedRulesInEngineOrderForAdminsAndMembers() throws Exception {
        Session admin = adminSession(); Session member = noraSession();
        long inProgress = statusId("IN_PROGRESS");
        long assign = createRule(admin, rule("Network to peer", "TASK_CREATED", null, otherWorkType, null, peer.id(), null, List.of()));
        long move = createRule(admin, rule("Urgent starts", "TASK_CREATED", null, null, "HIGH", null, inProgress, List.of()));
        createRule(admin, rule("Notify only", "TASK_CREATED", null, null, null, null, null, List.of(nora)));
        createRule(admin, rule("Other trigger", "ASSIGNEE_CHANGED", null, null, null, peer.id(), null, List.of()));
        long disabled = createRule(admin, rule("Disabled", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        jdbc.update("UPDATE project_automation_rules SET is_active=false WHERE rule_id=?", disabled);
        var leaving = com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Leaving Member");
        users.add(leaving.id());
        jdbc.update("INSERT INTO project_memberships(project_id,employee_id) VALUES (?,?)", project, leaving.id());
        createRule(admin, rule("Invalid target", "TASK_CREATED", null, null, null, leaving.id(), null, List.of()));
        jdbc.update("DELETE FROM project_memberships WHERE project_id=? AND employee_id=?", project, leaving.id());

        for (var request : List.of(get(rules() + "/preview").session(admin.session()),
                get("/api/projects/mine/" + project + "/automation-rules/preview").session(member.session()))) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].ruleId").value(assign))
                    .andExpect(jsonPath("$[0].name").value("Network to peer"))
                    .andExpect(jsonPath("$[0].nameAr").value("Network to peer AR"))
                    .andExpect(jsonPath("$[0].conditionWorkTypeId").value(otherWorkType))
                    .andExpect(jsonPath("$[0].assigneeId").value(peer.id()))
                    .andExpect(jsonPath("$[0].assigneeName").value("Workflow Peer"))
                    .andExpect(jsonPath("$[0].notifyUserIds").doesNotExist())
                    .andExpect(jsonPath("$[1].ruleId").value(move))
                    .andExpect(jsonPath("$[1].conditionPriority").value("HIGH"))
                    .andExpect(jsonPath("$[1].statusId").value(inProgress))
                    .andExpect(jsonPath("$[1].statusSemantic").value("IN_PROGRESS"));
        }

        // The preview matches what the engine does after save.
        long task = adminCreateTask(admin, nora, "HIGH", otherWorkType);
        assertThat(assigneeOf(task)).isEqualTo(peer.id());
        assertThat(statusOf(task)).isEqualTo(inProgress);

        // Members only: an outsider cannot read it, and members still cannot manage rules.
        var outsider = com.requestmanagement.backend.TestEmployeeFixtureSupport.createActiveEmployee(jdbc, "Preview Outsider");
        users.add(outsider.id());
        Session outsiderSession = login(outsider.email());
        mvc.perform(get("/api/projects/mine/" + project + "/automation-rules/preview").session(outsiderSession.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get(rules() + "/preview").session(member.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/mine/" + project + "/automation-rules/preview").session(admin.session()))
                .andExpect(status().isForbidden());

        // Archived Projects never run automation, so nothing is previewed.
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE project_id=?", project);
        mvc.perform(get("/api/projects/mine/" + project + "/automation-rules/preview").session(member.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anOverriddenSuggestedAssigneeIsKeptWhileTheRulesOtherActionsStillRun() throws Exception {
        Session admin = adminSession();
        long inProgress = statusId("IN_PROGRESS");
        long combined = createRule(admin, rule("Start and assign", "TASK_CREATED", null, null, null, peer.id(), inProgress, List.of(nora)));
        long assignOnly = createRule(admin, rule("Assign only", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        long reassignWatch = createRule(admin, rule("Watch reassignments", "ASSIGNEE_CHANGED", null, null, null, null, null, List.of(nora)));

        long task = createTask("/api/projects/" + project + "/tasks", admin, nora, true);

        assertThat(assigneeOf(task)).isEqualTo(nora);                     // the person's choice is kept
        assertThat(statusOf(task)).isEqualTo(inProgress);                // move status still runs
        assertThat(count("AUTOMATION_NOTIFY", task, nora)).isEqualTo(1); // notify still runs
        assertThat(lastResult(combined)).isEqualTo("APPLIED");
        assertThat(lastResult(assignOnly)).isEqualTo("NO_CHANGE");
        assertThat(lastResult(reassignWatch)).isNull();                  // no reassignment happened
        // The assignment is attributed to the person who created the Task, not to a rule.
        Map<String, Object> assigned = jdbc.queryForMap("""
                SELECT actor_id, automation_rule_id FROM user_notifications
                 WHERE request_id=? AND notification_type='TASK_ASSIGNED'
                """, task);
        assertThat(((Number) assigned.get("actor_id")).longValue()).isEqualTo(adminId);
        assertThat(assigned.get("automation_rule_id")).isNull();
        assertThat(count("TASK_ASSIGNED", task, peer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT created_by FROM requests WHERE request_id=?", Long.class, task)).isEqualTo(adminId);
        // The only history row is the rule's status move; nothing records a rule assignment.
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT new_project_status_id, automation_rule_id FROM status_history WHERE request_id=?", task);
        assertThat(history).hasSize(1);
        assertThat(((Number) history.get(0).get("new_project_status_id")).longValue()).isEqualTo(inProgress);
        assertThat(((Number) history.get(0).get("automation_rule_id")).longValue()).isEqualTo(combined);

        // Later "assignee changed" rules are unaffected by the creation-time override.
        mvc.perform(write(patch("/api/projects/" + project + "/tasks/" + task + "/assignee"), admin)
                .content("{\"assignedToId\":" + peer.id() + "}")).andExpect(status().is2xxSuccessful());
        assertThat(assigneeOf(task)).isEqualTo(peer.id());
        assertThat(lastResult(reassignWatch)).isEqualTo("APPLIED");
    }

    @Test
    void membersCanOverrideTooAndWithoutTheFlagTheRuleStillAssigns() throws Exception {
        Session admin = adminSession(); Session member = noraSession();
        createRule(admin, rule("Assign to peer", "TASK_CREATED", null, null, null, peer.id(), null, List.of()));
        String memberTasks = "/api/projects/mine/" + project + "/tasks";

        assertThat(assigneeOf(createTask(memberTasks, member, nora, true))).isEqualTo(nora);
        assertThat(assigneeOf(createTask(memberTasks, member, nora, false))).isEqualTo(peer.id());
        assertThat(assigneeOf(createTask(memberTasks, member, nora, null))).isEqualTo(peer.id());
        assertThat(assigneeOf(adminCreateTask(admin, nora, "MEDIUM", workType))).isEqualTo(peer.id());
    }

    private long createTask(String url, Session session, long assignee, Boolean manuallySelected) throws Exception {
        String flag = manuallySelected == null ? "" : ",\"assigneeManuallySelected\":" + manuallySelected;
        return body(mvc.perform(write(post(url), session)
                        .content("{\"title\":\"Form task\",\"description\":\"Details\",\"workTypeId\":" + workType
                                + ",\"priority\":\"MEDIUM\",\"assignedToId\":" + assignee + flag + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createRule(Session admin, String body) throws Exception {
        return body(mvc.perform(write(post(rules()), admin).content(body)).andExpect(status().isCreated())).get("id").asLong();
    }

    private String rules() {
        return "/api/projects/" + project + "/automation-rules";
    }

    private long count(String type, long task, long recipient) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE notification_type=? AND request_id=? AND recipient_id=?",
                Long.class, type, task, recipient);
    }

    private String lastResult(long rule) {
        return jdbc.queryForObject("SELECT last_run_result FROM project_automation_rules WHERE rule_id=?", String.class, rule);
    }

    private static String rule(String name, String trigger, Long triggerStatus, Long workType, String priority,
                               Long assignee, Long actionStatus, List<Long> notify) {
        return "{\"name\":\"" + name + "\",\"nameAr\":\"" + name + " AR\",\"trigger\":\"" + trigger
                + "\",\"triggerStatusId\":" + triggerStatus + ",\"conditionWorkTypeId\":" + workType
                + ",\"conditionPriority\":" + (priority == null ? "null" : "\"" + priority + "\"")
                + ",\"actionAssigneeId\":" + assignee + ",\"actionStatusId\":" + actionStatus
                + ",\"notifyUserIds\":" + notify + ",\"active\":true}";
    }
}
