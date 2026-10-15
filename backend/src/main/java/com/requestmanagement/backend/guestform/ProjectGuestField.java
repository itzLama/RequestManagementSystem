package com.requestmanagement.backend.guestform;

import com.requestmanagement.backend.project.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "project_guest_fields") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectGuestField {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "guest_field_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @Column(name = "field_label", nullable = false, length = 100) private String label;
    @Enumerated(EnumType.STRING) @Column(name = "field_type", nullable = false, length = 20) private GuestFieldType type;
    @Column(name = "is_required", nullable = false) private boolean required;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    public static ProjectGuestField create(Project project, String label, GuestFieldType type, boolean required, int order) {
        ProjectGuestField f = new ProjectGuestField(); LocalDateTime now = LocalDateTime.now(); f.project=project; f.label=label;
        f.type=type; f.required=required; f.displayOrder=order; f.active=true; f.createdAt=now; f.updatedAt=now; return f;
    }
    public void update(String label, GuestFieldType type, boolean required) { this.label=label; this.type=type; this.required=required; updatedAt=LocalDateTime.now(); }
    public void moveTo(int order) { displayOrder=order; updatedAt=LocalDateTime.now(); }
    public void retire() { active=false; updatedAt=LocalDateTime.now(); }
}
