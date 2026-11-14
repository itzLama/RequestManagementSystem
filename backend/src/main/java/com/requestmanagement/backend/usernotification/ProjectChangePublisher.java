package com.requestmanagement.backend.usernotification;

import com.requestmanagement.backend.request.Request;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectChangePublisher {
    private final ApplicationEventPublisher events;

    public void changed(Request task, ProjectChangeEvent.ChangeType change) {
        if (task == null || !task.isProjectTask() || task.getId() == null) return;
        changed(task.getProject().getId(), task.getId(), change);
    }

    public void changed(Long projectId, Long taskId, ProjectChangeEvent.ChangeType change) {
        if (projectId == null) return;
        events.publishEvent(new ProjectChangeEvent(projectId, taskId, change));
    }
}
