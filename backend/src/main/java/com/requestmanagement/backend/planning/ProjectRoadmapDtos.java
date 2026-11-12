package com.requestmanagement.backend.planning;

import com.requestmanagement.backend.project.ProjectStatus;

import java.time.LocalDate;
import java.util.List;

public final class ProjectRoadmapDtos {
    private ProjectRoadmapDtos() { }

    /**
     * One Project bar. The span is derived from its root Tasks' planning dates; both ends are null
     * when no root Task is scheduled. Overdue = due before {@code today} and not in a status that
     * counts as completed.
     */
    public record ProjectSpan(Long projectId, String projectName, ProjectStatus status,
                              LocalDate startDate, LocalDate endDate,
                              long totalTasks, long scheduledTasks, long overdueTasks) { }

    /** {@code today} is the server's planning date (Asia/Riyadh by default) used for overdue counts. */
    public record Response(LocalDate today, List<ProjectSpan> projects) { }
}
