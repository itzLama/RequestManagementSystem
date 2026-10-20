package com.requestmanagement.backend.guestcommunication;

import java.time.LocalDateTime;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guest_communications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestCommunication {
    public enum SenderType { TEAM, GUEST }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "communication_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private User sentBy;

    @Enumerated(EnumType.STRING) @Column(name="sender_type",nullable=false,length=20)
    private SenderType senderType;
    @Column(name="sender_name_snapshot",length=150) private String senderName;
    @Column(name="sender_email_snapshot",length=255) private String senderEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public static GuestCommunication create(Request request, User sender, String message) {
        GuestCommunication communication = new GuestCommunication();
        communication.request = request;
        communication.sentBy = sender;
        communication.senderType = SenderType.TEAM;
        communication.senderName = sender.getFullName();
        communication.senderEmail = sender.getEmail();
        communication.message = message;
        communication.sentAt = LocalDateTime.now();
        return communication;
    }

    public static GuestCommunication createGuest(Request request, String message) {
        GuestCommunication communication = new GuestCommunication();
        communication.request=request; communication.sentBy=null; communication.senderType=SenderType.GUEST;
        communication.senderName=request.getGuestName(); communication.senderEmail=request.getGuestEmail();
        communication.message=message; communication.sentAt=LocalDateTime.now(); return communication;
    }
}
