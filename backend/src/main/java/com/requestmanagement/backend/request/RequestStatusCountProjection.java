package com.requestmanagement.backend.request;

public interface RequestStatusCountProjection {
    RequestStatus getStatus();

    long getRequestCount();
}
