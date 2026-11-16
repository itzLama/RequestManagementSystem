package com.requestmanagement.backend.notification;

import com.requestmanagement.backend.projectstatus.ProjectStatusColumn;
import com.requestmanagement.backend.request.Request;
import com.requestmanagement.backend.request.RequestStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Human English status labels for guest emails, matching the UI (frontend {@code lib/request-display.ts}).
 * General Requests use the fixed labels; Project Tasks use their status column's own name.
 * Raw enum values (e.g. {@code IN_PROGRESS}) are never returned.
 */
final class GuestStatusLabels {
    private static final Map<RequestStatus, String> GENERAL = new EnumMap<>(Map.of(
            RequestStatus.NEW, "New",
            RequestStatus.IN_PROGRESS, "In Progress",
            RequestStatus.WAITING_USER, "Waiting User",
            RequestStatus.COMPLETED, "Completed",
            RequestStatus.REJECTED, "Rejected"));

    private GuestStatusLabels() { }

    static String english(Request request) {
        ProjectStatusColumn column = request.getProjectStatus();
        if (column != null) return column.getName();
        RequestStatus status = request.getStatus();
        return status == null ? request.statusDisplayName() : GENERAL.get(status);
    }
}
