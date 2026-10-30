package com.selim.dashboard.service;

import com.selim.dashboard.client.Feed;
import com.selim.dashboard.client.FineDustClient;
import com.selim.dashboard.client.SubwayClient;
import com.selim.dashboard.client.UpstreamHealthRegistry;
import com.selim.dashboard.client.WeatherClient;
import com.selim.dashboard.config.CacheConfig;
import com.selim.dashboard.domain.FineDustReading;
import com.selim.dashboard.domain.SubwayCongestion;
import com.selim.dashboard.domain.WeatherPoint;
import com.selim.dashboard.dto.DashboardResponse;
import com.selim.dashboard.dto.WeatherHeadline;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 클라이언트 호출을 캐시로 감싸 화면 새로고침마다 공공API 를 때리지 않게 한다.
 */
@Service
public class DashboardService {

    private final WeatherClient weatherClient;
    private final FineDustClient fineDustClient;
    private final SubwayClient subwayClient;
    private final UpstreamHealthRegistry healthRegistry;
    private final CacheManager cacheManager;

    public DashboardService(WeatherClient weatherClient,
                            FineDustClient fineDustClient,
                            SubwayClient subwayClient,
                            UpstreamHealthRegistry healthRegistry,
                            CacheManager cacheManager) {
        this.weatherClient = weatherClient;
        this.fineDustClient = fineDustClient;
        this.subwayClient = subwayClient;
        this.healthRegistry = healthRegistry;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CacheConfig.WEATHER, key = "'all'")
    public Feed<WeatherPoint> weather() {
        return weatherClient.fetch();
    }

    @Cacheable(cacheNames = CacheConfig.FINE_DUST, key = "#realmCode == null ? 'default' : #realmCode")
    public Feed<FineDustReading> fineDust(String realmCode) {
        return fineDustClient.fetch(realmCode);
    }

    @Cacheable(cacheNames = CacheConfig.SUBWAY, key = "#stationName == null ? 'all' : #stationName")
    public Feed<SubwayCongestion> subway(String stationName) {
        return subwayClient.fetch(stationName);
    }

    public DashboardResponse summary() {
        Feed<WeatherPoint> weather = weather();
        Feed<FineDustReading> fineDust = fineDust(null);
        Feed<SubwayCongestion> subway = subway(null);
        return new DashboardResponse(Instant.now(), weather, fineDust, subway, headline(weather.items()));
    }

    /** 강제로 캐시를 비운다. 호출 후 summary() 를 별도 호출로 타야 프록시를 거쳐 다시 채워진다. */
    public void evictAll() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    public java.util.Map<String, UpstreamHealthRegistry.Snapshot> upstreamHealth() {
        return healthRegistry.snapshot();
    }

    static WeatherHeadline headline(List<WeatherPoint> items) {
        if (items == null || items.isEmpty()) {
            return new WeatherHeadline(null, null, null, "정보 없음", null, null);
        }
        List<WeatherPoint> sorted = items.stream()
                .filter(item -> item.value() != null)
                .sorted(Comparator.comparing((WeatherPoint i) -> nullToEmpty(i.fcstDate()))
                        .thenComparing(i -> nullToEmpty(i.fcstTime())))
                .toList();
        WeatherPoint temp = first(sorted, "TMP");
        WeatherPoint feels = first(sorted, "TMP_FEEL");
        WeatherPoint pop = first(sorted, "POP");
        WeatherPoint sky = first(sorted, "SKY");
        return new WeatherHeadline(
                temp == null ? null : temp.value(),
                feels == null ? null : feels.value(),
                pop == null ? null : pop.value(),
                sky == null ? "정보 없음" : skyLabel(sky.value()),
                temp == null ? null : temp.fcstDate(),
                temp == null ? null : temp.fcstTime());
    }

    private static WeatherPoint first(List<WeatherPoint> sorted, String category) {
        return sorted.stream()
                .filter(item -> Objects.equals(category, item.category()))
                .findFirst()
                .orElse(null);
    }

    static String skyLabel(Double value) {
        if (value == null) {
            return "정보 없음";
        }
        int code = (int) Math.round(value);
        return switch (code) {
            case 1 -> "맑음";
            case 2, 3 -> "구름많음";
            case 4 -> "흐림";
            default -> "기타(" + code + ")";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
