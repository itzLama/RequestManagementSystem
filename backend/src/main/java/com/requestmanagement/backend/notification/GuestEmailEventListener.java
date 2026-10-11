package com.requestmanagement.backend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestEmailEventListener {
    private final GuestEmailService guestEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGuestEmailNotification(GuestEmailNotificationEvent event) {
        try {
            guestEmailService.send(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to send guest email notification for request {} after commit.",
                    event.getRequestId(),
                    exception);
        }
    }
}
