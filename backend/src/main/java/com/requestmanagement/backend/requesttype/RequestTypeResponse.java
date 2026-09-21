package com.requestmanagement.backend.requesttype;

public record RequestTypeResponse(Long id, String name) {
    public static RequestTypeResponse from(RequestType type) {
        return new RequestTypeResponse(type.getId(), type.getTypeName());
    }
}
