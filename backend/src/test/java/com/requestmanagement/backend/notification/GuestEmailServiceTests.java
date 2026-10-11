package com.requestmanagement.backend.notification;

import com.requestmanagement.backend.guestcommunication.GuestCommunication;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestEmailServiceTests {
    private JavaMailSender mailSender;
    private GuestEmailService guestEmailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        guestEmailService = new GuestEmailService(mailSender);
        ReflectionTestUtils.setField(guestEmailService, "fromAddress", "no-reply@masar.local");
    }

    @Test
    void formatsStatusChangeEmailFromPersistedGuestRequestSnapshot() {
        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(
                guestRequest(42L, RequestStatus.IN_PROGRESS)));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage email = captor.getValue();
        assertThat(email.getFrom()).isEqualTo("no-reply@masar.local");
        assertThat(email.getTo()).containsExactly("guest@example.com");
        assertThat(email.getSubject()).isEqualTo("[Masar] Request #42 status updated");
        assertThat(email.getText()).contains("Hello Guest User,")
                .contains("Request #42")
                .contains("IN_PROGRESS");
    }

    @Test
    void formatsExplicitMessageEmailFromPersistedCommunicationSnapshot() {
        GuestCommunication communication = guestCommunication(
                guestRequest(73L, RequestStatus.NEW), "Please provide more details.");

        guestEmailService.send(GuestEmailNotificationEvent.messageSent(communication));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage email = captor.getValue();
        assertThat(email.getSubject()).isEqualTo("[Masar] New message for Request #73");
        assertThat(email.getText()).contains("Request #73")
                .contains("Please provide more details.");
    }

    @Test
    void listenerContainsMailDeliveryFailure() {
        GuestEmailNotificationEvent event = GuestEmailNotificationEvent.messageSent(
                guestCommunication(guestRequest(73L, RequestStatus.NEW), "Message"));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        GuestEmailEventListener listener = new GuestEmailEventListener(guestEmailService);
        listener.onGuestEmailNotification(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private Request guestRequest(Long id, RequestStatus status) {
        Request request = mock(Request.class);
        when(request.isGuest()).thenReturn(true);
        when(request.getId()).thenReturn(id);
        when(request.getGuestName()).thenReturn("Guest User");
        when(request.getGuestEmail()).thenReturn("guest@example.com");
        when(request.getStatus()).thenReturn(status);
        return request;
    }

    private GuestCommunication guestCommunication(Request request, String message) {
        GuestCommunication communication = mock(GuestCommunication.class);
        when(communication.getRequest()).thenReturn(request);
        when(communication.getMessage()).thenReturn(message);
        return communication;
    }
}
