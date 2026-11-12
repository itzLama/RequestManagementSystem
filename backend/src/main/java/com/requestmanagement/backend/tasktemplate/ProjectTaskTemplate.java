package com.requestmanagement.backend.tasktemplate;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.projectworktype.ProjectWorkType;
import com.requestmanagement.backend.request.RequestPriority;
import com.requestmanagement.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_task_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTaskTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "template_name", nullable = false, length = 100)
    private String name;

    @Column(name = "template_name_ar", nullable = false, length = 100)
    private String nameAr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_type_id", nullable = false)
    private ProjectWorkType workType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestPriority priority;

    @Column(name = "default_title", nullable = false, length = 200)
    private String defaultTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_document", nullable = false, columnDefinition = "jsonb")
    private JsonNode descriptionDocument;

    @Column(name = "due_offset_days")
    private Integer dueOffsetDays;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    static ProjectTaskTemplate create(Project project, User creator, Values values) {
        ProjectTaskTemplate template = new ProjectTaskTemplate();
        template.project = project;
        template.createdBy = creator;
        template.createdAt = LocalDateTime.now();
        template.apply(values);
        return template;
    }

    void update(Values values) {
        apply(values);
    }

    private void apply(Values values) {
        this.name = values.name();
        this.nameAr = values.nameAr();
        this.workType = values.workType();
        this.priority = values.priority();
        this.defaultTitle = values.defaultTitle();
        this.description = values.description();
        this.descriptionDocument = values.descriptionDocument();
        this.dueOffsetDays = values.dueOffsetDays();
        this.updatedAt = LocalDateTime.now();
    }

    record Values(String name, String nameAr, ProjectWorkType workType, RequestPriority priority,
                  String defaultTitle, String description, JsonNode descriptionDocument,
                  Integer dueOffsetDays) { }
}
