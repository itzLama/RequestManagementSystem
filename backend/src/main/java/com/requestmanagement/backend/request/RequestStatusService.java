package com.requestmanagement.backend.request;

import com.requestmanagement.backend.statushistory.StatusHistory;
import com.requestmanagement.backend.statushistory.StatusHistoryRepository;
import com.requestmanagement.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequestStatusService {
    private final RequestRepository requestRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public boolean changeStatus(Request request, RequestStatus newStatus, User actor, String changeNote) {
        RequestStatus oldStatus = request.getStatus();
        if (oldStatus == newStatus) return false;
        request.changeStatus(newStatus);
        requestRepository.save(request);
        String note = changeNote == null || changeNote.isBlank()
                ? "Status changed from " + oldStatus + " to " + newStatus + "."
                : changeNote.trim();
        statusHistoryRepository.save(StatusHistory.create(request, oldStatus, newStatus, actor, note));
        return true;
    }
}
