package com.requestmanagement.backend.usernotification;

import com.requestmanagement.backend.automation.AutomationContext;
import com.requestmanagement.backend.comment.CommentRepository;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import com.requestmanagement.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Translates Project Task changes into notification events, one per recipient. Recipients are
 * resolved inside the business transaction and de-duplicated, so a person gets exactly one
 * notification per action even when they match several roles (creator, assignee, commenter).
 * The actor is never notified about their own action. Project access (Admin or current member)
 * is enforced again when the notification is recorded.
 */
@Component
@RequiredArgsConstructor
public class TaskNotificationPublisher {
    private final ApplicationEventPublisher events;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    /** The (new) assignee. */
    public void assigned(Request task, User actor) {
        Set<Long> recipients = new LinkedHashSet<>();
        addAssignee(recipients, task);
        publish(UserNotificationType.TASK_ASSIGNED, task, actorId(actor), null, recipients);
    }

    /** The assignee and the creator. */
    public void statusChanged(Request task, User actor) {
        Set<Long> recipients = new LinkedHashSet<>();
        addAssignee(recipients, task);
        addCreator(recipients, task);
        publish(UserNotificationType.TASK_STATUS_CHANGED, task, actorId(actor),
                task.getProjectStatus() == null ? null : task.getProjectStatus().getId(), recipients);
    }

    /** Every participant: creator, current assignee and everyone who has commented on the Task. */
    public void commented(Request task, User actor) {
        publish(UserNotificationType.TASK_COMMENTED, task, actorId(actor), null, participants(task));
    }

    /** Internal notes stay private: only Admin participants are notified, never Employees. */
    public void internalNote(Request task, User actor) {
        Set<Long> participants = participants(task);
        Set<Long> admins = new LinkedHashSet<>();
        userRepository.findAllById(participants).stream()
                .filter(user -> user.getRole() == User.Role.ADMIN && user.isActive())
                .forEach(user -> admins.add(user.getId()));
        participants.retainAll(admins);
        publish(UserNotificationType.TASK_INTERNAL_NOTE, task, actorId(actor), null, participants);
    }

    /** A guest message notifies the assignee, or every active Admin when the Task is unassigned. */
    public void guestMessage(Request task) {
        Set<Long> recipients = new LinkedHashSet<>();
        addAssignee(recipients, task);
        if (recipients.isEmpty()) {
            userRepository.findByActiveTrueAndRoleOrderByFullNameAscIdAsc(User.Role.ADMIN)
                    .forEach(admin -> recipients.add(admin.getId()));
        }
        publish(UserNotificationType.GUEST_MESSAGE, task, null, null, recipients);
    }

    private Set<Long> participants(Request task) {
        Set<Long> recipients = new LinkedHashSet<>();
        addCreator(recipients, task);
        addAssignee(recipients, task);
        if (task.getId() != null) recipients.addAll(commentRepository.findDistinctAuthorIdsByRequestId(task.getId()));
        return recipients;
    }

    private static void addAssignee(Set<Long> recipients, Request task) {
        if (task.getAssignedTo() != null && task.getAssignedTo().getId() != null) recipients.add(task.getAssignedTo().getId());
    }

    private static void addCreator(Set<Long> recipients, Request task) {
        if (task.getCreatedBy() != null && task.getCreatedBy().getId() != null) recipients.add(task.getCreatedBy().getId());
    }

    private static Long actorId(User actor) {
        return actor == null ? null : actor.getId();
    }

    /**
     * Workflow Automation "notify members" action. The recipients were chosen by an Administrator
     * on the rule; membership is enforced again when each notification is recorded.
     */
    public void automationNotify(Request task, Collection<Long> recipients) {
        publish(UserNotificationType.AUTOMATION_NOTIFY, task, null, null, new LinkedHashSet<>(recipients));
    }

    private void publish(UserNotificationType type, Request task, Long actorId, Long statusId, Set<Long> recipients) {
        if (task == null || !task.isProjectTask() || task.getId() == null) return;
        // Changes made by an automation rule are attributed to the rule, never to a person.
        AutomationContext.Rule rule = AutomationContext.current().orElse(null);
        Long actor = rule == null ? actorId : null;
        if (actor != null) recipients.remove(actor);
        for (Long recipient : recipients) {
            events.publishEvent(new TaskNotificationEvent(type, recipient, actor, task.getProject().getId(), task.getId(),
                    statusId, rule == null ? null : rule.id(), rule == null ? null : rule.name(),
                    rule == null ? null : rule.nameAr()));
        }
    }
}
