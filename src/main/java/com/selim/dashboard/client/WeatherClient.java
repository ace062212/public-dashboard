package com.selim.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.selim.dashboard.config.PublicDataProperties;
import com.selim.dashboard.domain.WeatherPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 기상청 단기예보(기초생활기상) 호출.
 * UriComponentsBuilder 로 만든 URI 객체를 그대로 넘긴다. 문자열로 넘기면 RestClient 가 한 번 더 인코딩해
 * 한글 파라미터가 깨진다.
 */
@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> CATEGORIES = Set.of("TMP", "POP", "REH", "SKY", "PTY", "TMP_FEEL", "WD10", "VEC");
    private static final List<String> BASE_TIMES = List.of("2300", "2000", "1700", "1400", "1100", "0800", "0500", "0200");

    private final RestClient restClient;
    private final PublicDataProperties props;
    private final UpstreamHealthRegistry health;

    public WeatherClient(RestClient.Builder publicApiRestClientBuilder,
                         PublicDataProperties props,
                         UpstreamHealthRegistry health) {
        this.restClient = publicApiRestClientBuilder.baseUrl(props.weather().baseUrl()).build();
        this.props = props;
        this.health = health;
    }

    public Feed<WeatherPoint> fetch() {
        PublicDataProperties.Weather cfg = props.weather();
        if (!cfg.configured()) {
            health.recordMock(Upstreams.WEATHER);
            return Feed.mock(MockData.weather());
        }

        String baseDate = currentDate();
        String baseTime = currentBaseTime();
        try {
            URI uri = UriComponentsBuilder.fromUriString(cfg.baseUrl() + cfg.path())
                    .queryParam("serviceKey", cfg.serviceKey())
                    .queryParam("type", "json")
                    .queryParam("base_date", baseDate)
                    .queryParam("base_time", baseTime)
                    .queryParam("nx", cfg.nx())
                    .queryParam("ny", cfg.ny())
                    .queryParam("numOfRows", 300)
                    .queryParam("pageNo", 1)
                    .encode()
                    .build()
                    .toUri();

            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !JsonNodes.isSuccessHeader(root)) {
                throw new IllegalStateException("기상청 응답 오류: " + JsonNodes.errorMessage(root));
            }
            List<WeatherPoint> points = parse(root);
            if (points.isEmpty()) {
                throw new IllegalStateException("기상청 응답에 예보 항목이 없습니다: " + JsonNodes.errorMessage(root));
            }
            health.recordLive(Upstreams.WEATHER);
            return Feed.live(points);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("기상청 단기예보 호출 실패(base_date={}, base_time={}): {}", baseDate, baseTime, e.getMessage());
            health.recordFailure(Upstreams.WEATHER, e.getMessage());
            return Feed.fallback(MockData.weather(), "기상청 호출 실패, 목 데이터 대체: " + e.getMessage());
        }
    }

    static List<WeatherPoint> parse(JsonNode root) {
        List<WeatherPoint> points = new ArrayList<>();
        for (JsonNode item : JsonNodes.extractItems(root)) {
            String category = JsonNodes.text(item, "category");
            if (category == null || !CATEGORIES.contains(category)) {
                continue;
            }
            BigDecimal value = JsonNodes.decimal(item, "fcstValue", "value");
            points.add(new WeatherPoint(
                    category,
                    label(category),
                    JsonNodes.text(item, "fcstDate", "baseDate"),
                    JsonNodes.text(item, "fcstTime", "baseTime"),
                    value == null ? null : value.doubleValue(),
                    unit(category)));
        }
        return points;
    }

    static String label(String category) {
        return switch (category) {
            case "TMP" -> "기온";
            case "TMP_FEEL" -> "체감온도";
            case "POP" -> "강수확률";
            case "PTY" -> "강수형태";
            case "REH" -> "습도";
            case "SKY" -> "하늘상태";
            case "WD10" -> "풍속";
            case "VEC" -> "풍향";
            default -> category;
        };
    }

    static String unit(String category) {
        return switch (category) {
            case "TMP", "TMP_FEEL" -> "℃";
            case "WD10" -> "m/s";
            case "POP", "REH" -> "%";
            case "VEC" -> "deg";
            default -> "";
        };
    }

    private String currentDate() {
        return LocalDate.now(SEOUL).format(BASIC_DATE);
    }

    /** 발표 시각이 아직 안 된 슬롯을 고르면 응답이 비므로 가장 최근 발표 시각을 쓴다. */
    private String currentBaseTime() {
        LocalTime now = LocalTime.now(SEOUL);
        int minutes = now.getHour() * 60 + now.getMinute();
        for (String baseTime : BASE_TIMES) {
            int slot = Integer.parseInt(baseTime.substring(0, 2)) * 60 + Integer.parseInt(baseTime.substring(2));
            if (minutes >= slot + 30) {
                return baseTime;
            }
        }
        return "2300";
    }
}
