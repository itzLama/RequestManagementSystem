package com.requestmanagement.backend.project;

import com.requestmanagement.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_memberships", uniqueConstraints =
        @UniqueConstraint(name = "uq_project_memberships_project_employee", columnNames = {"project_id", "employee_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ProjectMembership create(Project project, User employee) {
        ProjectMembership membership = new ProjectMembership();
        membership.project = project;
        membership.employee = employee;
        membership.createdAt = LocalDateTime.now();
        return membership;
    }
}
