package com.requestmanagement.backend.guesttracking;

import com.requestmanagement.backend.request.Request;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="guest_tracking_challenges") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class GuestTrackingChallenge {
    @Id @Column(name="challenge_id") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="request_id") private Request request;
    @Column(name="lookup_digest",nullable=false,length=64) private String lookupDigest;
    @Column(name="otp_digest",nullable=false,length=64) private String otpDigest;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="failed_attempts",nullable=false) private int failedAttempts;
    @Column(name="sent_count",nullable=false) private int sentCount;
    @Column(name="last_sent_at") private LocalDateTime lastSentAt;
    @Column(name="consumed_at") private LocalDateTime consumedAt;
    @Column(name="superseded_at") private LocalDateTime supersededAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;

    static GuestTrackingChallenge create(Request request,String lookup,String digest,LocalDateTime now) {
        var c=new GuestTrackingChallenge(); c.id=UUID.randomUUID(); c.request=request;c.lookupDigest=lookup;c.otpDigest=digest;
        c.expiresAt=now.plusMinutes(10);c.failedAttempts=0;c.sentCount=1;c.lastSentAt=now;c.createdAt=now;return c;
    }
    boolean available(LocalDateTime now){return consumedAt==null&&supersededAt==null&&expiresAt.isAfter(now)&&failedAttempts<5;}
    void fail(){failedAttempts++;}
    void consume(LocalDateTime now){consumedAt=now;}
    void supersede(LocalDateTime now){supersededAt=now;}
    void resend(String digest,LocalDateTime now){otpDigest=digest;expiresAt=now.plusMinutes(10);failedAttempts=0;lastSentAt=now;sentCount++;}
}
