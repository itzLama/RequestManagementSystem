package com.requestmanagement.backend.dashboard;

import com.requestmanagement.backend.request.RequestRepository;
import com.requestmanagement.backend.request.RequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RequestRepository requestRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        EnumMap<RequestStatus, Long> counts = new EnumMap<>(RequestStatus.class);
        Arrays.stream(RequestStatus.values()).forEach(status -> counts.put(status, 0L));
        requestRepository.countRequestsByStatus()
                .forEach(result -> counts.put(result.getStatus(), result.getRequestCount()));

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long open = counts.get(RequestStatus.NEW)
                + counts.get(RequestStatus.IN_PROGRESS)
                + counts.get(RequestStatus.WAITING_USER);
        long completed = counts.get(RequestStatus.COMPLETED);

        var statusCounts = counts.entrySet().stream()
                .map(entry -> new DashboardResponse.StatusCount(entry.getKey(), entry.getValue()))
                .toList();
        var latestRequests = requestRepository.findTop5ByProjectIsNullOrderByCreatedAtDescIdDesc().stream()
                .map(DashboardResponse.LatestRequest::from)
                .toList();

        return new DashboardResponse(total, open, completed, statusCounts, latestRequests);
    }
}
