package com.selim.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.selim.dashboard.domain.SubwayCongestion;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SubwayClientTest {

    private MockRestServiceServer server;
    private UpstreamHealthRegistry health;
    private SubwayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        health = new UpstreamHealthRegistry();
        client = new SubwayClient(builder, TestProps.of("weather-key", "dust-key", "subway-key"), health);
    }

    private static String stationJson(String stationName, String rate) {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"items":{"item":[
                   {"lineId":"02","lineName":"2호선","stationId":"0222","stationName":"%s","congestionRate":"%s","trainNo":"511","updateDate":"20261030093000"}
                 ]}}}}"""
                .formatted(stationName, rate);
    }

    @Test
    @DisplayName("서비스 키가 없으면 목 데이터를 반환한다")
    void returnsMockWithoutKey() {
        SubwayClient keyless = new SubwayClient(RestClient.builder(), TestProps.of("", "", ""), health);

        Feed<SubwayCongestion> feed = keyless.fetch(null);

        assertThat(feed.source()).isEqualTo(Feed.Source.MOCK);
        assertThat(feed.items()).extracting(SubwayCongestion::stationName).contains("강남", "홍대입구");
        assertThat(health.snapshot().get("subway").status()).isEqualTo("MOCK");
        server.verify();
    }

    @Test
    @DisplayName("목 데이터도 역 이름으로 걸러 반환한다")
    void filtersMockDataByStationName() {
        SubwayClient keyless = new SubwayClient(RestClient.builder(), TestProps.of("", "", ""), health);

        Feed<SubwayCongestion> feed = keyless.fetch("강남");

        assertThat(feed.items()).singleElement().satisfies(station -> {
            assertThat(station.stationName()).isEqualTo("강남");
            assertThat(station.level()).isEqualTo("매우혼잡");
        });
    }

    @Test
    @DisplayName("한글 역 이름 파라미터가 한 번만 인코딩되어 그대로 도착한다")
    void sendsKoreanStationNameSingleEncoded() {
        String keyword = "세종대로";
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        server.expect(requestTo(Matchers.containsString("/subway")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("lineId", "02"))
                .andExpect(request -> {
                    // getQuery() 는 디코딩된 값이라, 이중 인코딩이면 여기서 %EC... 형태로 남는다.
                    assertThat(request.getURI().getQuery()).contains("stationNm=" + keyword);
                    String rawQuery = request.getURI().getRawQuery();
                    assertThat(rawQuery).contains("stationNm=" + encoded);
                    assertThat(rawQuery).doesNotContain("%25");
                })
                .andRespond(withSuccess(stationJson(keyword, "171"), MediaType.APPLICATION_JSON));

        Feed<SubwayCongestion> feed = client.fetch(keyword);

        assertThat(feed.source()).isEqualTo(Feed.Source.LIVE);
        assertThat(feed.items()).singleElement().satisfies(station -> {
            assertThat(station.stationName()).isEqualTo(keyword);
            assertThat(station.level()).isEqualTo("매우혼잡");
        });
        server.verify();
    }

    @Test
    @DisplayName("역 이름을 주지 않으면 stationNm 파라미터를 보내지 않는다")
    void omitsStationParamWhenAbsent() {
        server.expect(requestTo(Matchers.containsString("/subway")))
                .andExpect(request -> assertThat(request.getURI().getRawQuery()).doesNotContain("stationNm"))
                .andRespond(withSuccess(stationJson("서울역", "88"), MediaType.APPLICATION_JSON));

        Feed<SubwayCongestion> feed = client.fetch(null);

        assertThat(feed.items()).singleElement().satisfies(station -> {
            assertThat(station.stationName()).isEqualTo("서울역");
            assertThat(station.lineName()).isEqualTo("2호선");
            assertThat(station.level()).isEqualTo("보통");
        });
    }

    @Test
    @DisplayName("응답의 등급이 없으면 혼잡도 값으로 등급을 계산한다")
    void computesCongestionLevelLocally() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":{"item":[
                  {"stationName":"A","congestionRate":"70"},
                  {"stationName":"B","congestionRate":"120"},
                  {"stationName":"C","congestionRate":"140"},
                  {"stationName":"D","congestionRate":"180"}
                ]}}}}""";
        server.expect(requestTo(Matchers.containsString("/subway")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.fetch(null).items()).extracting(SubwayCongestion::level)
                .containsExactly("여유", "보통", "혼잡", "매우혼잡");
    }

    @Test
    @DisplayName("값이 없는 행은 버린다")
    void skipsRowsWithoutRate() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":{"item":[
                  {"stationName":"이름없음"},
                  {"congestionRate":"120"},
                  {"stationName":"을지로입구","congestionRate":"105"}
                ]}}}}""";
        server.expect(requestTo(Matchers.containsString("/subway")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.fetch(null).items()).extracting(SubwayCongestion::stationName)
                .containsExactly("을지로입구");
    }

    @Test
    @DisplayName("업스트림 장애 시 목 데이터로 대체한다")
    void degradesOnServerError() {
        server.expect(requestTo(Matchers.containsString("/subway"))).andRespond(withServerError());

        Feed<SubwayCongestion> feed = client.fetch("신촌");

        assertThat(feed.source()).isEqualTo(Feed.Source.FALLBACK);
        assertThat(feed.message()).contains("지하철 혼잡도 호출 실패");
        assertThat(feed.items()).singleElement().satisfies(station ->
                assertThat(station.stationName()).isEqualTo("신촌"));
        assertThat(health.snapshot().get("subway").failureCount()).isEqualTo(1);
    }
}
