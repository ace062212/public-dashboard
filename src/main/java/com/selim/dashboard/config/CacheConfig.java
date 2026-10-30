package com.selim.dashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    public static final String WEATHER = "weather";
    public static final String FINE_DUST = "fineDust";
    public static final String SUBWAY = "subway";

    public static final List<String> NAMES = List.of(WEATHER, FINE_DUST, SUBWAY);

    /**
     * 공공API 는 호출 빈도가 낮아도 응답이 느리므로 캐시가 사실상 필수다.
     * TTL 은 캐시 만료 후 첫 요청에서 다시 호출하는 방식이며, 백그라운드 갱신은 RefreshScheduler 가 담당한다.
     */
    @Bean
    public CacheManager cacheManager(PublicDataProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(NAMES.toArray(String[]::new));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(props.cacheTtl())
                .maximumSize(1_000)
                .recordStats());
        return manager;
    }
}
