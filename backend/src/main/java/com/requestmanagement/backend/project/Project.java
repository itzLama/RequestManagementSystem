package com.requestmanagement.backend.project;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "project_name", nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Project create(String name, String description) {
        Project project = new Project();
        LocalDateTime now = LocalDateTime.now();
        project.name = name;
        project.description = description;
        project.status = ProjectStatus.ACTIVE;
        project.createdAt = now;
        project.updatedAt = now;
        return project;
    }

    public void update(String name, String description) {
        requireActive();
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void archive() {
        if (status == ProjectStatus.ACTIVE) {
            status = ProjectStatus.ARCHIVED;
            updatedAt = LocalDateTime.now();
        }
    }

    public boolean isActive() {
        return status == ProjectStatus.ACTIVE;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Archived projects cannot be modified.");
        }
    }
}
