package com.requestmanagement.backend.request;

import java.util.List;

import org.springframework.data.domain.Page;

public record AdminRequestsPageResponse(
        List<AdminRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static AdminRequestsPageResponse from(Page<AdminRequestResponse> results) {
        return new AdminRequestsPageResponse(
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isFirst(),
                results.isLast()
        );
    }
}
