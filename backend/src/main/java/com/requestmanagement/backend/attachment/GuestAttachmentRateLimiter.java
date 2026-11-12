package com.requestmanagement.backend.attachment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class GuestAttachmentRateLimiter {
    private static final int MAX_SESSIONS = 10_000;
    private final Map<UUID, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    void acquire(UUID sessionId, int limit) {
        Instant cutoff = Instant.now().minusSeconds(3600);
        if (attempts.size() >= MAX_SESSIONS && !attempts.containsKey(sessionId)) evict(cutoff);
        if (attempts.size() >= MAX_SESSIONS && !attempts.containsKey(sessionId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Guest upload capacity is busy.");
        }
        ArrayDeque<Instant> session = attempts.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        synchronized (session) {
            while (!session.isEmpty() && session.peekFirst().isBefore(cutoff)) session.removeFirst();
            if (session.size() >= limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Guest attachment upload limit reached. Please try again later.");
            session.addLast(Instant.now());
        }
    }

    private void evict(Instant cutoff) {
        Iterator<Map.Entry<UUID, ArrayDeque<Instant>>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            synchronized (entry.getValue()) {
                while (!entry.getValue().isEmpty() && entry.getValue().peekFirst().isBefore(cutoff)) {
                    entry.getValue().removeFirst();
                }
                if (entry.getValue().isEmpty()) attempts.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
