package com.projectmgmt.portal;

import com.projectmgmt.common.ApiException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SubmitRateLimiter {

    private static final int LIMIT = 10;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(String ip, String module) {
        String key = (ip == null ? "unknown" : ip) + ":" + module;
        long now = Instant.now().toEpochMilli();
        Deque<Long> times = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= LIMIT) {
                throw ApiException.tooManyRequests("提交过于频繁，请稍后再试");
            }
            times.addLast(now);
        }
    }

    public void reset() {
        hits.clear();
    }
}
