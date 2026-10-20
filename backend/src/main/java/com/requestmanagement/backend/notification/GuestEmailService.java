package com.requestmanagement.backend.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GuestEmailService {
    private static final String TRACKING_INSTRUCTION =
            "You can track your request through the Masar website using your tracking number and registered email.";

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
            case REQUEST_SUBMITTED -> "[Masar] Request #" + event.getRequestId() + " submitted";
            case VERIFICATION_CODE -> "[Masar] Verification code for Request #" + event.getRequestId();
        };
    }

    private String body(GuestEmailNotificationEvent event) {
        return switch (event.getCategory()) {
            case REQUEST_STATUS_CHANGED -> !event.isProjectTrackingAvailable()
                    ? String.format(Locale.ROOT,
                            "Hello %s,%n%nThe status of Request #%d has changed to %s.%n%nMasar",
                            event.getGuestName(), event.getRequestId(), event.getStatus())
                    : String.format(Locale.ROOT,
                            "Hello %s,%n%nThe status of Request #%d has changed to %s.%n%n%s%n%nMasar",
                            event.getGuestName(), event.getRequestId(), event.getStatus(),
                            TRACKING_INSTRUCTION);
            case MESSAGE_SENT -> !event.isProjectTrackingAvailable()
                    ? String.format(Locale.ROOT,
                            "Hello %s,%n%nThere is a new message for Request #%d:%n%n%s%n%nMasar",
                            event.getGuestName(), event.getRequestId(), event.getMessage())
                    : String.format(Locale.ROOT,
                            "Hello %s,%n%nThere is a new message for Request #%d.%n%n%s%n%nMasar",
                            event.getGuestName(), event.getRequestId(), TRACKING_INSTRUCTION);
            case REQUEST_SUBMITTED -> String.format(Locale.ROOT,
                    "Hello %s,%n%nYour Masar request #%d was submitted successfully.%n%n%s%n%nMasar",
                    event.getGuestName(), event.getRequestId(), TRACKING_INSTRUCTION);
            case VERIFICATION_CODE -> String.format(Locale.ROOT,
                    "Hello %s,%n%nYour Masar verification code is %s.%nIt expires in 10 minutes. Do not share this code.%n%n%s%n%nMasar",
                    event.getGuestName(), event.getVerificationCode(), TRACKING_INSTRUCTION);
        };
    }
}
