package com.selim.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.selim.dashboard.config.PublicDataProperties;
import com.selim.dashboard.domain.FineDustReading;
import com.selim.dashboard.domain.Grades;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 환경부 측정정보서비스 권역별 실측 농도 호출.
 */
@Component
public class FineDustClient {

    private static final Logger log = LoggerFactory.getLogger(FineDustClient.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final List<String> ITEMS = List.of("PM10", "PM25", "O3", "NO2");

    private final RestClient restClient;
    private final PublicDataProperties props;
    private final UpstreamHealthRegistry health;

    public FineDustClient(RestClient.Builder publicApiRestClientBuilder,
                          PublicDataProperties props,
                          UpstreamHealthRegistry health) {
        this.restClient = publicApiRestClientBuilder.baseUrl(props.fineDust().baseUrl()).build();
        this.props = props;
        this.health = health;
    }

    /**
     * @param realmCode 조회 권역 코드. null 이면 설정값을 쓴다.
     */
    public Feed<FineDustReading> fetch(String realmCode) {
        PublicDataProperties.FineDust cfg = props.fineDust();
        if (!cfg.configured()) {
            health.recordMock(Upstreams.FINE_DUST);
            return Feed.mock(MockData.fineDust());
        }

        String realm = StringUtils.hasText(realmCode) ? realmCode : cfg.realmCode();
        LocalDateTime end = LocalDateTime.now(SEOUL);
        LocalDateTime start = end.minusDays(1);
        try {
            URI uri = UriComponentsBuilder.fromUriString(cfg.baseUrl() + cfg.path())
                    .queryParam("serviceKey", cfg.serviceKey())
                    .queryParam("resultType", "json")
                    .queryParam("numOfRows", 300)
                    .queryParam("pageNo", 1)
                    .queryParam("items", String.join(",", ITEMS))
                    .queryParam("realmCd", realm)
                    .queryParam("startDateTime", start.format(STAMP))
                    .queryParam("endDateTime", end.format(STAMP))
                    .encode()
                    .build()
                    .toUri();

            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !JsonNodes.isSuccessHeader(root)) {
                throw new IllegalStateException("대기오염도 응답 오류: " + JsonNodes.errorMessage(root));
            }
            List<FineDustReading> readings = parse(root);
            if (readings.isEmpty()) {
                throw new IllegalStateException("대기오염도 응답에 실측 값이 없습니다: " + JsonNodes.errorMessage(root));
            }
            health.recordLive(Upstreams.FINE_DUST);
            return Feed.live(readings);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("대기오염도 호출 실패(realmCd={}): {}", realm, e.getMessage());
            health.recordFailure(Upstreams.FINE_DUST, e.getMessage());
            return Feed.fallback(MockData.fineDust(), "대기오염도 호출 실패, 목 데이터 대체: " + e.getMessage());
        }
    }

    static List<FineDustReading> parse(JsonNode root) {
        List<FineDustReading> readings = new ArrayList<>();
        for (JsonNode item : JsonNodes.extractItems(root)) {
            String itemName = JsonNodes.text(item, "itemName", "items", "pollutants", "itemNm");
            if (itemName == null) {
                continue;
            }
            itemName = itemName.toUpperCase();
            BigDecimal value = JsonNodes.decimal(item, "value", "pmValue1h", "pmValue24h", "avgValue", "dataValue");
            if (value == null) {
                continue;
            }
            String grade = JsonNodes.text(item, "grade", "itemGrade", "tierNm");
            if (grade == null) {
                grade = gradeOf(itemName, value.doubleValue());
            }
            readings.add(new FineDustReading(
                    JsonNodes.text(item, "realmNm", "regionName", "cityName", "stationName"),
                    itemName,
                    label(itemName),
                    value.doubleValue(),
                    grade,
                    JsonNodes.text(item, "dateTimeStr", "updateDate", "measureTime", "date")));
        }
        return readings;
    }

    static String label(String itemName) {
        return switch (itemName) {
            case "PM10" -> "미세먼지";
            case "PM25" -> "초미세먼지";
            case "O3" -> "오존";
            case "NO2" -> "이산화질소";
            case "CO" -> "일산화탄소";
            case "SO2" -> "아황산가스";
            default -> itemName;
        };
    }

    static String gradeOf(String itemName, double value) {
        return switch (itemName) {
            case "PM10" -> Grades.pm10(value);
            case "PM25" -> Grades.pm25(value);
            case "O3" -> Grades.ozone(value);
            case "NO2" -> Grades.nitrogenDioxide(value);
            default -> Grades.none(value);
        };
    }
}
