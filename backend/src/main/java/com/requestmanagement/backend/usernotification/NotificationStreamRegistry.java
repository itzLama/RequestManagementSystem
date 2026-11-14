package com.requestmanagement.backend.usernotification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-user Server-Sent Event connections for live notification delivery.
 *
 * <p>Single-instance limitation: emitters live in this JVM's memory. With more than one backend
 * instance, a notification created on instance A is not pushed to a browser connected to
 * instance B; that browser still receives it on its next periodic refresh (the client polls as a
 * fallback). Multi-instance push would need a shared broker (e.g. PostgreSQL LISTEN/NOTIFY).
 */
@Component
@Slf4j
public class NotificationStreamRegistry {
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    static final int MAX_STREAMS_PER_USER = 5;

    private final Map<Long, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(Long userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        Deque<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        userStreams.add(emitter);
        while (userStreams.size() > MAX_STREAMS_PER_USER) {
            SseEmitter oldest = userStreams.pollFirst();
            if (oldest != null) oldest.complete();
        }
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> { remove.run(); emitter.complete(); });
        emitter.onError(error -> remove.run());
        send(userId, emitter, SseEmitter.event().name("ready").reconnectTime(5000)
                .data(new UserNotificationDtos.UnreadCount(unreadCount), MediaType.APPLICATION_JSON));
        return emitter;
    }

    public void sendNotification(Long userId, UserNotificationDtos.Item item, long unreadCount) {
        broadcast(userId, "notification", new NotificationPush(item, unreadCount));
    }

    /** Live board refresh signal (references and change type only). */
    public void sendProjectChange(Long userId, ProjectChangeEvent event) {
        broadcast(userId, "project-change", event);
    }

    /** Users that currently hold at least one open stream. */
    public java.util.Set<Long> connectedUserIds() {
        return java.util.Set.copyOf(streams.keySet());
    }

    public void sendUnreadCount(Long userId, long unreadCount) {
        broadcast(userId, "unread", new UserNotificationDtos.UnreadCount(unreadCount));
    }

    /** Keeps idle connections (and intermediaries) alive and detects closed browsers. */
    @Scheduled(fixedDelayString = "${masar.notifications.heartbeat-interval:25s}")
    public void heartbeat() {
        streams.forEach((userId, userStreams) -> userStreams.forEach(emitter ->
                send(userId, emitter, SseEmitter.event().comment("heartbeat"))));
    }

    int openStreamCount(Long userId) {
        Deque<SseEmitter> userStreams = streams.get(userId);
        return userStreams == null ? 0 : userStreams.size();
    }

    private void broadcast(Long userId, String name, Object payload) {
        Deque<SseEmitter> userStreams = streams.get(userId);
        if (userStreams == null) return;
        userStreams.forEach(emitter -> send(userId, emitter,
                SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON)));
    }

    private void send(Long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            log.debug("Closing notification stream for user {}: {}", userId, exception.getMessage());
            remove(userId, emitter);
            emitter.completeWithError(exception);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, userStreams) -> {
            userStreams.remove(emitter);
            return userStreams.isEmpty() ? null : userStreams;
        });
    }

    public record NotificationPush(UserNotificationDtos.Item notification, long unreadCount) { }
}
