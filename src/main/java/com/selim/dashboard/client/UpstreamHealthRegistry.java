package com.selim.dashboard.client;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * 소스별 호출 성공/실패 이력. /api/health/upstreams 에서 실데이터 여부를 확인한다.
 */
@Component
public class UpstreamHealthRegistry {

    public record Snapshot(String source,
                           String status,
                           Instant lastAttemptAt,
                           Instant lastSuccessAt,
                           String lastError,
                           long successCount,
                           long failureCount) {
    }

    private static final class State {
        private final LongAdder success = new LongAdder();
        private final LongAdder failure = new LongAdder();
        private volatile String status = "INIT";
        private volatile Instant lastAttemptAt;
        private volatile Instant lastSuccessAt;
        private volatile String lastError;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public void recordLive(String source) {
        State state = state(source);
        state.success.increment();
        state.status = "LIVE";
        state.lastAttemptAt = Instant.now();
        state.lastSuccessAt = state.lastAttemptAt;
        state.lastError = null;
    }

    public void recordMock(String source) {
        State state = state(source);
        state.status = "MOCK";
        state.lastAttemptAt = Instant.now();
    }

    public void recordFailure(String source, String message) {
        State state = state(source);
        state.failure.increment();
        state.status = "FALLBACK";
        state.lastAttemptAt = Instant.now();
        state.lastError = message;
    }

    public Map<String, Snapshot> snapshot() {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        states.forEach((name, state) -> result.put(name, new Snapshot(
                name, state.status, state.lastAttemptAt, state.lastSuccessAt,
                state.lastError, state.success.sum(), state.failure.sum())));
        return result;
    }

    private State state(String source) {
        return states.computeIfAbsent(source, key -> new State());
    }
}
