package com.requestmanagement.backend.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestEmailService {
    private final JavaMailSender mailSender;

    @Value("${masar.mail.from}")
    private String fromAddress;

    public void send(GuestEmailNotificationEvent event) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(event.getRecipientEmail());
        email.setSubject(subject(event));
        email.setText(body(event));
        mailSender.send(email);
    }

    private String subject(GuestEmailNotificationEvent event) {
        return switch (event.getCategory()) {
            case REQUEST_STATUS_CHANGED ->
                    "[Masar] Request #" + event.getRequestId() + " status updated";
            case MESSAGE_SENT ->
                    "[Masar] New message for Request #" + event.getRequestId();
        };
    }

    private String body(GuestEmailNotificationEvent event) {
        return switch (event.getCategory()) {
            case REQUEST_STATUS_CHANGED -> String.format(
                    "Hello %s,%n%nThe status of Request #%d has changed to %s.%n%nMasar",
                    event.getGuestName(), event.getRequestId(), event.getStatus());
            case MESSAGE_SENT -> String.format(
                    "Hello %s,%n%nThere is a new message for Request #%d:%n%n%s%n%nMasar",
                    event.getGuestName(), event.getRequestId(), event.getMessage());
        };
    }
}
