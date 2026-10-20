package com.requestmanagement.backend.guesttracking;

import com.requestmanagement.backend.request.Request;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="guest_tracking_sessions") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class GuestTrackingSession {
    @Id @Column(name="session_id") private UUID id;
    @Column(name="token_digest",nullable=false,length=64) private String tokenDigest;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="request_id",nullable=false) private Request request;
    @Column(name="last_activity_at",nullable=false) private LocalDateTime lastActivityAt;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="revoked_at") private LocalDateTime revokedAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    static GuestTrackingSession create(Request request,String digest,LocalDateTime now){var s=new GuestTrackingSession();s.id=UUID.randomUUID();s.request=request;s.tokenDigest=digest;s.lastActivityAt=now;s.expiresAt=now.plusMinutes(30);s.createdAt=now;return s;}
    boolean active(LocalDateTime now){return revokedAt==null&&expiresAt.isAfter(now);}
    void touch(LocalDateTime now){lastActivityAt=now;expiresAt=now.plusMinutes(30);}
    void revoke(LocalDateTime now){revokedAt=now;}
}
