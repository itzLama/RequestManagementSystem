package com.requestmanagement.backend.guestform;

import com.requestmanagement.backend.project.Project;
import com.requestmanagement.backend.projectworktype.ProjectWorkType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "project_guest_form_settings") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectGuestFormSettings {
    @Id @Column(name = "project_id") private Long projectId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "project_id") private Project project;
    @Column(name = "work_type_visible", nullable = false) private boolean workTypeVisible;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "default_work_type_id") private ProjectWorkType defaultWorkType;
    @Column(name = "priority_visible", nullable = false) private boolean priorityVisible;
    @Column(name = "priority_required", nullable = false) private boolean priorityRequired;
    @Column(name = "description_visible", nullable = false) private boolean descriptionVisible;
    @Column(name = "description_required", nullable = false) private boolean descriptionRequired;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static ProjectGuestFormSettings defaults(Project project) {
        ProjectGuestFormSettings value = new ProjectGuestFormSettings(); value.project = project;
        value.workTypeVisible = true; value.priorityVisible = true; value.priorityRequired = true;
        value.descriptionVisible = true; value.descriptionRequired = true; value.updatedAt = LocalDateTime.now(); return value;
    }
    public void update(boolean workTypeVisible, ProjectWorkType defaultWorkType, boolean priorityVisible,
                       boolean priorityRequired, boolean descriptionVisible, boolean descriptionRequired) {
        this.workTypeVisible = workTypeVisible; this.defaultWorkType = defaultWorkType;
        this.priorityVisible = priorityVisible; this.priorityRequired = priorityVisible && priorityRequired;
        this.descriptionVisible = descriptionVisible; this.descriptionRequired = descriptionVisible && descriptionRequired;
        this.updatedAt = LocalDateTime.now();
    }
}
