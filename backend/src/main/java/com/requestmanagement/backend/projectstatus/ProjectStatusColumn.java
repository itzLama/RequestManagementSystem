package com.requestmanagement.backend.projectstatus;

import com.requestmanagement.backend.project.Project;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="project_status_columns") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProjectStatusColumn {
    public enum SemanticKey { NEW, IN_PROGRESS, WAITING_USER, COMPLETED, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="status_id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="project_id",nullable=false) private Project project;
    @Column(name="status_name",nullable=false,length=50) private String name;
    @Enumerated(EnumType.STRING) @Column(name="semantic_key",length=30) private SemanticKey semanticKey;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(name="is_active",nullable=false) private boolean active;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Version private long version;
    public static ProjectStatusColumn create(Project p,String name,SemanticKey key,int order){var s=new ProjectStatusColumn();s.project=p;s.name=name;s.semanticKey=key;s.displayOrder=order;s.active=true;s.createdAt=LocalDateTime.now();s.updatedAt=s.createdAt;return s;}
    public void rename(String value){name=value;updatedAt=LocalDateTime.now();}
    public void moveTo(int order){displayOrder=order;updatedAt=LocalDateTime.now();}
    public void retire(){active=false;updatedAt=LocalDateTime.now();}
    public boolean isProtectedNew(){return semanticKey==SemanticKey.NEW;}
}
