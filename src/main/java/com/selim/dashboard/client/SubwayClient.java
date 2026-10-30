package com.selim.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.selim.dashboard.config.PublicDataProperties;
import com.selim.dashboard.domain.Grades;
import com.selim.dashboard.domain.SubwayCongestion;
import java.math.BigDecimal;
import java.net.URI;
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
 * 서울교통공사 역사별 혼잡도 호출.
 * stationName 은 한글 파라미터라 반드시 URI 객체로 전달한다 (문자열로 넘기면 재인코딩으로 깨짐).
 */
@Component
public class SubwayClient {

    private static final Logger log = LoggerFactory.getLogger(SubwayClient.class);

    private final RestClient restClient;
    private final PublicDataProperties props;
    private final UpstreamHealthRegistry health;

    public SubwayClient(RestClient.Builder publicApiRestClientBuilder,
                        PublicDataProperties props,
                        UpstreamHealthRegistry health) {
        this.restClient = publicApiRestClientBuilder.baseUrl(props.subway().baseUrl()).build();
        this.props = props;
        this.health = health;
    }

    /**
     * @param stationName 특정 역사만 좁혀달 요청. null 이면 호선 전체.
     */
    public Feed<SubwayCongestion> fetch(String stationName) {
        PublicDataProperties.Subway cfg = props.subway();
        if (!cfg.configured()) {
            health.recordMock(Upstreams.SUBWAY);
            return Feed.mock(filter(MockData.subway(), stationName));
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(cfg.baseUrl() + cfg.path())
                    .queryParam("serviceKey", cfg.serviceKey())
                    .queryParam("type", "json")
                    .queryParam("numOfRows", 200)
                    .queryParam("pageNo", 1)
                    .queryParam("lineId", cfg.lineId());
            if (StringUtils.hasText(stationName)) {
                builder.queryParam("stationNm", stationName);
            }
            URI uri = builder.encode().build().toUri();

            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !JsonNodes.isSuccessHeader(root)) {
                throw new IllegalStateException("지하철 혼잡도 응답 오류: " + JsonNodes.errorMessage(root));
            }
            List<SubwayCongestion> stations = parse(root);
            if (stations.isEmpty()) {
                throw new IllegalStateException("지하철 혼잡도 응답에 역 정보가 없습니다: " + JsonNodes.errorMessage(root));
            }
            health.recordLive(Upstreams.SUBWAY);
            return Feed.live(stations);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("지하철 혼잡도 호출 실패(lineId={}, stationName={}): {}", cfg.lineId(), stationName, e.getMessage());
            health.recordFailure(Upstreams.SUBWAY, e.getMessage());
            return Feed.fallback(filter(MockData.subway(), stationName),
                    "지하철 혼잡도 호출 실패, 목 데이터 대체: " + e.getMessage());
        }
    }

    static List<SubwayCongestion> parse(JsonNode root) {
        List<SubwayCongestion> result = new ArrayList<>();
        for (JsonNode item : JsonNodes.extractItems(root)) {
            String stationName = JsonNodes.text(item, "stationName", "stationNm", "statnName", "station");
            BigDecimal rate = JsonNodes.decimal(item, "congestionRate", "congestion", "usngRate", "rate");
            if (stationName == null || rate == null) {
                continue;
            }
            String level = JsonNodes.text(item, "congestionGrade", "congestionLevel", "gradeNm");
            if (level == null) {
                level = Grades.congestion(rate.doubleValue());
            }
            result.add(new SubwayCongestion(
                    JsonNodes.text(item, "lineId", "lineNo", "line"),
                    JsonNodes.text(item, "lineName", "lineNm", "lnnm"),
                    JsonNodes.text(item, "stationId", "stationNo", "statnId"),
                    stationName,
                    rate.doubleValue(),
                    level,
                    JsonNodes.text(item, "trainNo", "train"),
                    JsonNodes.text(item, "updateDate", "insertDtm", "dataDate")));
        }
        return result;
    }

    static List<SubwayCongestion> filter(List<SubwayCongestion> items, String stationName) {
        if (!StringUtils.hasText(stationName)) {
            return items;
        }
        return items.stream()
                .filter(item -> item.stationName() != null && item.stationName().contains(stationName))
                .toList();
    }
}
