package com.requestmanagement.backend.usernotification;

import com.requestmanagement.backend.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The caller's own notifications only; the recipient is always the authenticated user. */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@RequiredArgsConstructor
public class UserNotificationController {
    private final UserNotificationService service;
    private final NotificationStreamRegistry streams;

    @GetMapping
    public UserNotificationDtos.Page list(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.list(principal.userId(), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    public UserNotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UserNotificationDtos.UnreadCount(service.unreadCount(principal.userId()));
    }

    @PostMapping("/{id}/read")
    public UserNotificationDtos.UnreadCount markRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable Long id) {
        long unread = service.markRead(principal.userId(), id);
        streams.sendUnreadCount(principal.userId(), unread);
        return new UserNotificationDtos.UnreadCount(unread);
    }

    @PostMapping("/read-all")
    public UserNotificationDtos.UnreadCount markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        long unread = service.markAllRead(principal.userId());
        streams.sendUnreadCount(principal.userId(), unread);
        return new UserNotificationDtos.UnreadCount(unread);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletResponse response) {
        // no-transform keeps proxies (including the Next.js dev rewrite) from compressing/buffering the stream.
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(principal.userId(), service.unreadCount(principal.userId()));
    }
}
