package com.requestmanagement.backend.attachment;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_attachment_visibility_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAttachmentVisibilityEvent {
    public enum Action { SHARED, UNSHARED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visibility_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private TaskAttachment attachment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, insertable = false, updatable = false)
    private Request request;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_visibility", nullable = false, length = 30)
    private TaskAttachment.Visibility previousVisibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_visibility", nullable = false, length = 30)
    private TaskAttachment.Visibility newVisibility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    static TaskAttachmentVisibilityEvent create(TaskAttachment attachment,
                                                TaskAttachment.Visibility previous,
                                                TaskAttachment.Visibility target,
                                                User actor) {
        TaskAttachmentVisibilityEvent event = new TaskAttachmentVisibilityEvent();
        event.attachment = attachment;
        event.requestId = attachment.getRequestId();
        event.projectId = attachment.getProjectId();
        event.action = target == TaskAttachment.Visibility.GUEST_VISIBLE ? Action.SHARED : Action.UNSHARED;
        event.previousVisibility = previous;
        event.newVisibility = target;
        event.actor = actor;
        event.occurredAt = LocalDateTime.now();
        return event;
    }
}
