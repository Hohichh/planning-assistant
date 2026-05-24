package io.hohichh.planning_assistant.web;

import io.hohichh.planning_assistant.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentRateLimitInterceptor implements HandlerInterceptor {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("/api/v1/users/([^/]+)/agent/process");

    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;

    public AgentRateLimitInterceptor(
            @Value("${planning.ai.rate-limit.requests:10}") int maxRequests,
            @Value("${planning.ai.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!uri.matches(".*/api/v1/users/[^/]+/agent/process$")) {
            return true;
        }

        String key = extractKey(uri, request.getRemoteAddr());
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                throw new RateLimitExceededException("Too many AI planning requests. Please try again later.");
            }
            timestamps.addLast(now);
        }

        return true;
    }

    private String extractKey(String uri, String fallback) {
        Matcher matcher = USER_ID_PATTERN.matcher(uri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return fallback == null ? "unknown" : fallback;
    }
}
