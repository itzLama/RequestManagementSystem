package com.requestmanagement.backend.automation;

import com.requestmanagement.backend.request.Request;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Raises automation triggers for root Project Tasks (subtasks never trigger automation). Changes
 * made by an automation rule raise nothing (loop prevention, see {@link AutomationContext}).
 */
@Component
@RequiredArgsConstructor
public class AutomationTriggerPublisher {
    private final ApplicationEventPublisher events;

    public void taskCreated(Request task) {
        taskCreated(task, false);
    }

    /** {@code keepAssignee}: the creator overrode the suggested assignee, so assign actions skip this Task. */
    public void taskCreated(Request task, boolean keepAssignee) {
        publish(task, AutomationTrigger.TASK_CREATED, null, keepAssignee);
    }

    public void statusChanged(Request task) {
        publish(task, AutomationTrigger.STATUS_CHANGED,
                task == null || task.getProjectStatus() == null ? null : task.getProjectStatus().getId(), false);
    }

    public void assigneeChanged(Request task) {
        publish(task, AutomationTrigger.ASSIGNEE_CHANGED, null, false);
    }

    private void publish(Request task, AutomationTrigger trigger, Long statusId, boolean keepAssignee) {
        if (AutomationContext.current().isPresent()) return;
        if (task == null || !task.isProjectTask() || task.isSubtask() || task.getId() == null) return;
        events.publishEvent(new AutomationTriggerEvent(task.getProject().getId(), task.getId(), trigger, statusId, keepAssignee));
    }
}
