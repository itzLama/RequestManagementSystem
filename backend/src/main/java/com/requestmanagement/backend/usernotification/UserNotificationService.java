package com.requestmanagement.backend.usernotification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Every query is scoped to the authenticated recipient (no IDOR). Notifications for Projects the
 * recipient is no longer a member of are hidden (kept, but never listed, counted or marked).
 */
@Service
@RequiredArgsConstructor
public class UserNotificationService {
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * The recipient can still see the Project (an Administrator, or a current member) and, for
     * internal notes, is an Administrator.
     */
    private static final String VISIBLE = """
            EXISTS (SELECT 1 FROM users ru
                     WHERE ru.user_id = n.recipient_id
                       AND (ru.role = 'ADMIN'
                            OR (n.notification_type <> 'TASK_INTERNAL_NOTE'
                                AND EXISTS (SELECT 1 FROM project_memberships m
                                             WHERE m.project_id = n.project_id AND m.employee_id = n.recipient_id))))
            """;

    private static final String SELECT_ITEMS = """
            SELECT n.notification_id, n.notification_type, n.project_id, p.project_name,
                   n.request_id, r.title, r.parent_request_id, r.guest_name, n.actor_id, a.full_name AS actor_name,
                   n.project_status_id, s.status_name, s.semantic_key, n.created_at, n.read_at
              FROM user_notifications n
              JOIN projects p ON p.project_id = n.project_id
              JOIN requests r ON r.request_id = n.request_id
              LEFT JOIN users a ON a.user_id = n.actor_id
              LEFT JOIN project_status_columns s ON s.status_id = n.project_status_id
            """;

    private static final RowMapper<UserNotificationDtos.Item> ITEM = (rs, row) -> new UserNotificationDtos.Item(
            rs.getLong("notification_id"),
            UserNotificationType.valueOf(rs.getString("notification_type")),
            rs.getLong("project_id"), rs.getString("project_name"),
            rs.getLong("request_id"), rs.getString("title"),
            rs.getObject("parent_request_id", Long.class), rs.getString("guest_name"),
            rs.getObject("actor_id", Long.class), rs.getString("actor_name"),
            rs.getObject("project_status_id", Long.class), rs.getString("status_name"), rs.getString("semantic_key"),
            toLocal(rs.getTimestamp("created_at")), toLocal(rs.getTimestamp("read_at")));

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Persists a notification in its own transaction (called after the business commit). Skips
     * self-notifications, inactive recipients, recipients who can no longer access the Project
     * (only Administrators and current members can), and internal notes for non-Administrators.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserNotificationDtos.Item> record(TaskNotificationEvent event) {
        if (event.recipientId() == null || event.recipientId().equals(event.actorId())) return Optional.empty();
        var params = new MapSqlParameterSource()
                .addValue("recipient", event.recipientId())
                .addValue("type", event.type().name())
                .addValue("project", event.projectId())
                .addValue("task", event.taskId())
                .addValue("actor", event.actorId())
                .addValue("status", event.statusId())
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()));
        List<Long> ids = jdbc.queryForList("""
                INSERT INTO user_notifications(recipient_id, notification_type, project_id, request_id,
                                               actor_id, project_status_id, created_at)
                SELECT :recipient, :type, :project, :task, :actor, :status, :now
                 WHERE EXISTS (SELECT 1 FROM users u
                                WHERE u.user_id = :recipient AND u.is_active
                                  AND (u.role = 'ADMIN'
                                       OR (:type <> 'TASK_INTERNAL_NOTE'
                                           AND EXISTS (SELECT 1 FROM project_memberships m
                                                        WHERE m.project_id = :project AND m.employee_id = :recipient))))
                   AND EXISTS (SELECT 1 FROM requests r WHERE r.request_id = :task AND r.project_id = :project)
                RETURNING notification_id
                """, params, Long.class);
        if (ids.isEmpty()) return Optional.empty();
        return jdbc.query(SELECT_ITEMS + " WHERE n.notification_id = :id",
                new MapSqlParameterSource("id", ids.get(0)), ITEM).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public UserNotificationDtos.Page list(Long userId, int page, int size, boolean unreadOnly) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var params = new MapSqlParameterSource("user", userId)
                .addValue("limit", safeSize + 1).addValue("offset", (long) safePage * safeSize);
        List<UserNotificationDtos.Item> rows = jdbc.query(SELECT_ITEMS + """
                 WHERE n.recipient_id = :user AND %s %s
                 ORDER BY n.created_at DESC, n.notification_id DESC
                 LIMIT :limit OFFSET :offset
                """.formatted(VISIBLE, unreadOnly ? "AND n.read_at IS NULL" : ""), params, ITEM);
        boolean hasMore = rows.size() > safeSize;
        return new UserNotificationDtos.Page(hasMore ? rows.subList(0, safeSize) : rows,
                safePage, safeSize, hasMore, unreadCount(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications n WHERE n.recipient_id = :user AND n.read_at IS NULL AND "
                + VISIBLE, new MapSqlParameterSource("user", userId), Long.class);
        return count == null ? 0 : count;
    }

    /** Marks one of the caller's own visible notifications as read. Anything else is "not found". */
    @Transactional
    public long markRead(Long userId, Long notificationId) {
        var params = new MapSqlParameterSource("user", userId).addValue("id", notificationId)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now()));
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications n WHERE n.notification_id = :id AND n.recipient_id = :user AND "
                + VISIBLE, params, Integer.class);
        if (owned == null || owned == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found.");
        }
        jdbc.update("""
                UPDATE user_notifications SET read_at = :now
                 WHERE notification_id = :id AND recipient_id = :user AND read_at IS NULL
                """, params);
        return unreadCount(userId);
    }

    @Transactional
    public long markAllRead(Long userId) {
        jdbc.update("UPDATE user_notifications n SET read_at = :now WHERE n.recipient_id = :user AND n.read_at IS NULL AND "
                + VISIBLE, new MapSqlParameterSource("user", userId).addValue("now", Timestamp.valueOf(LocalDateTime.now())));
        return unreadCount(userId);
    }

    private static LocalDateTime toLocal(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
