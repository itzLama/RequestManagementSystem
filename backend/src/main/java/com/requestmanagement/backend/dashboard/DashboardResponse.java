package com.requestmanagement.backend.dashboard;

import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestPriority;
import com.requestmanagement.backend.request.RequestStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        long totalRequests,
        long openRequests,
        long completedRequests,
        List<StatusCount> requestsByStatus,
        List<LatestRequest> latestRequests
) {
    public record StatusCount(RequestStatus status, long count) {
    }

    public record LatestRequest(
            Long id,
            String title,
            String typeName,
            RequestPriority priority,
            RequestStatus status,
            LocalDateTime createdAt
    ) {
        public static LatestRequest from(Request request) {
            return new LatestRequest(
                    request.getId(),
                    request.getTitle(),
                    request.getType().getTypeName(),
                    request.getPriority(),
                    request.getStatus(),
                    request.getCreatedAt()
            );
        }
    }
}
