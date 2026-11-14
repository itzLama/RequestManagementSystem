package com.requestmanagement.backend.usernotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;

/**
 * Pushes {@link ProjectChangeEvent}s after commit to connected users who can access the Project:
 * active Administrators and current active members. Guests never hold a stream. Runs immediately
 * when published outside a transaction (e.g. permanent deletion, which manages its own
 * transactions). Failures are logged and never affect the business operation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectChangeListener {
    private final NamedParameterJdbcTemplate jdbc;
    private final NotificationStreamRegistry streams;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProjectChange(ProjectChangeEvent event) {
        try {
            Set<Long> connected = streams.connectedUserIds();
            if (connected.isEmpty()) return;
            List<Long> recipients = jdbc.queryForList("""
                    SELECT u.user_id FROM users u
                     WHERE u.user_id IN (:connected) AND u.is_active
                       AND (u.role = 'ADMIN'
                            OR EXISTS (SELECT 1 FROM project_memberships m
                                        WHERE m.project_id = :project AND m.employee_id = u.user_id))
                    """, new MapSqlParameterSource("connected", connected).addValue("project", event.projectId()), Long.class);
            recipients.forEach(userId -> streams.sendProjectChange(userId, event));
        } catch (RuntimeException exception) {
            log.warn("Unable to push live update for project {} task {}.", event.projectId(), event.taskId(), exception);
        }
    }
}
