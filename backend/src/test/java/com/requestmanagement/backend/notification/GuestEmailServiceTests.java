package com.requestmanagement.backend.notification;

import com.requestmanagement.backend.guestcommunication.GuestCommunication;
import com.requestmanagement.backend.projectstatus.ProjectStatusColumn;
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
                .contains("The status of Request #42 has changed to In Progress.")
                .doesNotContain("IN_PROGRESS")
                .doesNotContain("----------");
        // English only: no Arabic characters.
        assertThat(email.getText()).doesNotContainPattern("[\\u0600-\\u06FF]");
    }

    @Test
    void statusEmailNeverShowsRawEnumValuesForAnyGeneralStatus() {
        for (RequestStatus status : RequestStatus.values()) {
            guestEmailService.send(GuestEmailNotificationEvent.statusChanged(guestRequest(7L, status)));
        }
        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(RequestStatus.values().length)).send(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(email -> assertThat(email.getText())
                .doesNotContain("NEW", "IN_PROGRESS", "WAITING_USER", "COMPLETED", "REJECTED"));
        assertThat(captor.getAllValues().get(4).getText()).contains("changed to Rejected.");
        assertThat(captor.getAllValues().get(2).getText()).contains("changed to Waiting User.");
    }

    @Test
    void projectStatusEmailUsesTheColumnsOwnName() {
        Request defaultColumn = guestRequest(8L, null);
        when(defaultColumn.getProjectStatus()).thenReturn(ProjectStatusColumn.create(null, "Rejected", ProjectStatusColumn.SemanticKey.REJECTED, 4));
        Request renamedColumn = guestRequest(9L, null);
        when(renamedColumn.getProjectStatus()).thenReturn(ProjectStatusColumn.create(null, "Declined", ProjectStatusColumn.SemanticKey.REJECTED, 4));
        Request customColumn = guestRequest(10L, null);
        when(customColumn.getProjectStatus()).thenReturn(ProjectStatusColumn.create(null, "QA Review", null, 3));

        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(defaultColumn));
        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(renamedColumn));
        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(customColumn));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(3)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).getText()).contains("changed to Rejected.");
        assertThat(captor.getAllValues().get(1).getText()).contains("changed to Declined.").doesNotContain("REJECTED");
        assertThat(captor.getAllValues().get(2).getText()).contains("changed to QA Review.");
        assertThat(captor.getAllValues()).allSatisfy(email -> assertThat(email.getText()).doesNotContainPattern("[\\u0600-\\u06FF]"));
    }

    @Test
    void statusEmailStripsLineBreaksAndDirectionOverridesFromDynamicValues() {
        Request request = guestRequest(11L, RequestStatus.COMPLETED);
        when(request.getGuestName()).thenReturn("Eve\r\nBcc: x@example.com\u202E");

        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(request));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("Hello Eve Bcc: x@example.com,")
                .doesNotContain("\u202E").doesNotContain("Eve\r\n");
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

    @Test
    void projectGuestEmailsContainTrackingInstructionsWithoutTrackingUrls() {
        Request request = guestRequest(91L, RequestStatus.WAITING_USER);
        when(request.isProjectTask()).thenReturn(true);

        guestEmailService.send(GuestEmailNotificationEvent.statusChanged(request));
        guestEmailService.send(GuestEmailNotificationEvent.messageSent(
                guestCommunication(request, "Sensitive reply body")));
        guestEmailService.send(GuestEmailNotificationEvent.submitted(request));
        guestEmailService.send(GuestEmailNotificationEvent.verification(request, "123456"));

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(4)).send(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(email ->
                assertThat(email.getText())
                        .contains("You can track your request through the Masar website using your tracking number and registered email.")
                        .doesNotContain("localhost", "http://", "https://", "/track-request"));
        assertThat(captor.getAllValues().get(1).getText()).doesNotContain("Sensitive reply body");
        assertThat(captor.getAllValues().get(2).getText()).contains("#91");
        assertThat(captor.getAllValues().get(3).getText())
                .contains("123456")
                .contains("expires in 10 minutes");
    }

    private Request guestRequest(Long id, RequestStatus status) {
        Request request = mock(Request.class);
        when(request.isGuest()).thenReturn(true);
        when(request.getId()).thenReturn(id);
        when(request.getGuestName()).thenReturn("Guest User");
        when(request.getGuestEmail()).thenReturn("guest@example.com");
        when(request.getStatus()).thenReturn(status);
        when(request.statusDisplayName()).thenReturn(status == null ? "" : status.name());
        return request;
    }

    private GuestCommunication guestCommunication(Request request, String message) {
        GuestCommunication communication = mock(GuestCommunication.class);
        when(communication.getRequest()).thenReturn(request);
        when(communication.getMessage()).thenReturn(message);
        return communication;
    }
}
