package com.selim.dashboard.client;

import java.time.Instant;
import java.util.List;

/**
 * 하나의 데이터 소스 응답. source 가 LIVE 가 아니면 화면과 /api/health/upstreams 에서 구분해 표시한다.
 */
public record Feed<T>(Source source, Instant fetchedAt, List<T> items, String message) {

    public enum Source {
        /** 실제 공공API 응답 */
        LIVE,
        /** 서비스 키 미설정으로 만든 더미 데이터 */
        MOCK,
        /** 업스트림 장애로 이전 데이터/더미 데이터로 대체 */
        FALLBACK
    }

    public static <T> Feed<T> live(List<T> items) {
        return new Feed<>(Source.LIVE, Instant.now(), List.copyOf(items), null);
    }

    public static <T> Feed<T> mock(List<T> items) {
        return new Feed<>(Source.MOCK, Instant.now(), List.copyOf(items), "서비스 키 미설정: 목 데이터");
    }

    public static <T> Feed<T> fallback(List<T> items, String message) {
        return new Feed<>(Source.FALLBACK, Instant.now(), List.copyOf(items), message);
    }

    public boolean live() {
        return source == Source.LIVE;
    }
}
