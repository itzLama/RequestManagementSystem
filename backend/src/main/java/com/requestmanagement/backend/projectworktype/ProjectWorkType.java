package com.requestmanagement.backend.projectworktype;

import com.requestmanagement.backend.project.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_work_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectWorkType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_type_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "work_type_name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ProjectWorkType create(Project project, String name) {
        ProjectWorkType type = new ProjectWorkType();
        LocalDateTime now = LocalDateTime.now();
        type.project = project;
        type.name = name;
        type.active = true;
        type.createdAt = now;
        type.updatedAt = now;
        return type;
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public void retire() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }
}
