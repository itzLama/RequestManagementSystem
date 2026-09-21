package com.requestmanagement.backend.request;

import java.util.List;

import org.springframework.data.domain.Page;

public record MyRequestsPageResponse(
        List<MyRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static MyRequestsPageResponse from(Page<MyRequestResponse> results) {
        return new MyRequestsPageResponse(
                results.getContent(), results.getNumber(), results.getSize(),
                results.getTotalElements(), results.getTotalPages(),
                results.isFirst(), results.isLast()
        );
    }
}
