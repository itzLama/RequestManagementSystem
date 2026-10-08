package com.requestmanagement.backend.request;

import java.util.List;

import org.springframework.data.domain.Page;

public record AssignedTasksPageResponse(
        List<AssignedTaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AssignedTasksPageResponse from(Page<AssignedTaskResponse> results) {
        return new AssignedTasksPageResponse(
                results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(),
                results.isFirst(), results.isLast()
        );
    }
}
