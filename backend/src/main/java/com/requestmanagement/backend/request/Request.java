package com.requestmanagement.backend.request;

import java.time.LocalDateTime;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.requesttype.RequestType;
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

@Entity
@Table(name = "requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", length = 20)
    private ProjectWorkType workType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "guest_name", length = 150)
    private String guestName;

    @Column(name = "guest_email", length = 255)
    private String guestEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Request create(String title, String description, RequestType type,
                                 RequestPriority priority, User creator) {
        return create(title, description, type, priority, creator, null);
    }

    private static Request create(String title, String description, RequestType type,
                                  RequestPriority priority, User creator, Project project) {
        Request request = new Request();
        request.title = title;
        request.description = description;
        request.type = type;
        request.workType = null;
        request.priority = priority;
        request.status = RequestStatus.NEW;
        request.createdBy = creator;
        request.guestName = null;
        request.guestEmail = null;
        request.project = project;
        request.assignedTo = null;
        request.createdAt = LocalDateTime.now();
        request.updatedAt = request.createdAt;
        return request;
    }

    public static Request createProjectTask(String title, String description, ProjectWorkType workType,
                                            RequestPriority priority, User creator, User assignee,
                                            Project project) {
        if (project == null) throw new IllegalArgumentException("Project is required.");
        if (workType == null) throw new IllegalArgumentException("Work type is required.");
        if (assignee == null) throw new IllegalArgumentException("Assignee is required.");
        Request task = new Request();
        task.title = title;
        task.description = description;
        task.type = null;
        task.workType = workType;
        task.priority = priority;
        task.status = RequestStatus.NEW;
        task.createdBy = creator;
        task.guestName = null;
        task.guestEmail = null;
        task.project = project;
        task.assignedTo = assignee;
        task.createdAt = LocalDateTime.now();
        task.updatedAt = task.createdAt;
        return task;
    }

    public void changeStatus(RequestStatus newStatus) {
        status = newStatus;
        updatedAt = LocalDateTime.now();
    }

    public void assignTo(User newAssignee) {
        assignedTo = newAssignee;
        updatedAt = LocalDateTime.now();
    }

    public String requesterName() {
        return createdBy != null ? createdBy.getFullName() : guestName;
    }

    public String requesterEmail() {
        return createdBy != null ? createdBy.getEmail() : guestEmail;
    }

    public boolean isGeneralRequest() {
        return project == null;
    }

    public boolean isProjectTask() {
        return project != null;
    }

}
