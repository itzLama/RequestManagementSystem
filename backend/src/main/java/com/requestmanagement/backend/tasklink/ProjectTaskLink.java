package com.requestmanagement.backend.tasklink;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_task_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTaskLink {
    public enum RelationshipType { BLOCKS, DUPLICATES, RELATES_TO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_task_id", nullable = false)
    private Request sourceTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_task_id", nullable = false)
    private Request targetTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private RelationshipType relationshipType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ProjectTaskLink create(Project project, Request source, Request target,
                                         RelationshipType type, User actor) {
        ProjectTaskLink link = new ProjectTaskLink();
        link.project = project;
        link.sourceTask = source;
        link.targetTask = target;
        link.relationshipType = type;
        link.createdBy = actor;
        link.createdAt = LocalDateTime.now();
        return link;
    }
}
