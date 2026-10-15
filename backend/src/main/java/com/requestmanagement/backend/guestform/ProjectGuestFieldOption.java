package com.requestmanagement.backend.guestform;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "project_guest_field_options") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectGuestFieldOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "option_id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_field_id") private ProjectGuestField field;
    @Column(name = "option_label", nullable = false, length = 100) private String label;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    public static ProjectGuestFieldOption create(ProjectGuestField field, String label, int order) { ProjectGuestFieldOption o=new ProjectGuestFieldOption(); LocalDateTime now=LocalDateTime.now(); o.field=field;o.label=label;o.displayOrder=order;o.active=true;o.createdAt=now;o.updatedAt=now;return o; }
    public void configure(String label, int order) { this.label=label; this.displayOrder=order; this.active=true; updatedAt=LocalDateTime.now(); }
    public void retire() { active=false; updatedAt=LocalDateTime.now(); }
}
