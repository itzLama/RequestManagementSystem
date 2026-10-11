package com.requestmanagement.backend.request;

import com.requestmanagement.backend.notification.GuestEmailNotificationEvent;
import com.requestmanagement.backend.statushistory.StatusHistory;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestStatusService {
    private final RequestRepository requestRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public boolean changeStatus(Request request, RequestStatus newStatus, User actor, String changeNote) {
        RequestStatus oldStatus = request.getStatus();
        if (oldStatus == newStatus) return false;
        request.changeStatus(newStatus);
        requestRepository.save(request);
        String note = changeNote == null || changeNote.isBlank()
                ? "Status changed from " + oldStatus + " to " + newStatus + "."
                : changeNote.trim();
        statusHistoryRepository.save(StatusHistory.create(request, oldStatus, newStatus, actor, note));
        if (request.isGuest()) {
            eventPublisher.publishEvent(GuestEmailNotificationEvent.statusChanged(request));
        }
        return true;
    }
}
