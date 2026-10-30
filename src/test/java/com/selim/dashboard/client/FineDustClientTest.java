package com.selim.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.selim.dashboard.domain.FineDustReading;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FineDustClientTest {

    private MockRestServiceServer server;
    private UpstreamHealthRegistry health;
    private FineDustClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        health = new UpstreamHealthRegistry();
        client = new FineDustClient(builder, TestProps.of("weather-key", "dust-key", "subway-key"), health);
    }

    @Test
    @DisplayName("서비스 키가 없으면 목 데이터를 반환한다")
    void returnsMockWithoutKey() {
        FineDustClient keyless = new FineDustClient(RestClient.builder(), TestProps.of("", "", ""), health);

        Feed<FineDustReading> feed = keyless.fetch(null);

        assertThat(feed.source()).isEqualTo(Feed.Source.MOCK);
        assertThat(feed.items()).extracting(FineDustReading::itemName).contains("PM10", "PM25");
        assertThat(health.snapshot().get("fineDust").status()).isEqualTo("MOCK");
        server.verify();
    }

    @Test
    @DisplayName("권역 코드와 오염물질을 파라미터로 보낸다")
    void sendsRealmAndItems() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"items":{"item":[{"realmNm":"서울","itemName":"PM10","value":"23","dateTimeStr":"202610300900"}]}}}}""";
        server.expect(requestTo(Matchers.containsString("/dust")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("realmCd", "11"))
                .andExpect(queryParam("items", "PM10,PM25,O3,NO2"))
                .andExpect(queryParam("resultType", "json"))
                .andExpect(queryParam("serviceKey", "dust-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<FineDustReading> feed = client.fetch(null);

        assertThat(feed.source()).isEqualTo(Feed.Source.LIVE);
        assertThat(feed.items()).singleElement().satisfies(reading -> {
            assertThat(reading.regionName()).isEqualTo("서울");
            assertThat(reading.itemLabel()).isEqualTo("미세먼지");
            assertThat(reading.value()).isEqualTo(23.0);
            assertThat(reading.grade()).isEqualTo("좋음");
        });
        server.verify();
    }

    @Test
    @DisplayName("호출자가 준 권역 코드가 설정값을 덮어쓴다")
    void overridesRealmCode() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},
                 "body":{"items":{"item":[{"realmNm":"제주","itemName":"PM25","value":"12"}]}}}}""";
        server.expect(requestTo(Matchers.containsString("/dust")))
                .andExpect(queryParam("realmCd", "49"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<FineDustReading> feed = client.fetch("49");

        assertThat(feed.items()).singleElement().satisfies(reading -> {
            assertThat(reading.regionName()).isEqualTo("제주");
            assertThat(reading.grade()).isEqualTo("좋음");
        });
        server.verify();
    }

    @Test
    @DisplayName("등급이 없는 응답도 값으로 등급을 계산한다")
    void computesGradeLocally() {
        String expected = "나쁨";
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},
                 "body":{"items":{"item":[
                   {"realmNm":"세종","itemName":"PM10","value":"96"},
                   {"realmNm":"세종","itemName":"PM10","value":"151"},
                   {"realmNm":"세종","itemName":"PM10","value":"44"}
                 ]}}}}""";
        server.expect(requestTo(Matchers.containsString("/dust")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<FineDustReading> feed = client.fetch(null);

        assertThat(feed.items()).extracting(FineDustReading::grade)
                .containsExactly(expected, "매우나쁨", "보통");
        assertThat(feed.items()).allSatisfy(reading -> assertThat(reading.regionName()).isEqualTo("세종"));
    }

    @Test
    @DisplayName("값이 없거나 항목명이 없는 행은 버린다")
    void skipsIncompleteRows() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},
                 "body":{"items":{"item":[
                   {"realmNm":"경기","itemName":"PM10","value":"null"},
                   {"realmNm":"경기","value":"30"},
                   {"realmNm":"경기","itemName":"O3","value":"0.05"}
                 ]}}}}""";
        server.expect(requestTo(Matchers.containsString("/dust")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<FineDustReading> feed = client.fetch(null);

        assertThat(feed.items()).singleElement().satisfies(reading -> {
            assertThat(reading.itemName()).isEqualTo("O3");
            assertThat(reading.grade()).isEqualTo("보통");
        });
    }

    @Test
    @DisplayName("업스트림 장애 시 목 데이터로 대체한다")
    void degradesOnServerError() {
        server.expect(requestTo(Matchers.containsString("/dust"))).andRespond(withServerError());

        Feed<FineDustReading> feed = client.fetch("11");

        assertThat(feed.source()).isEqualTo(Feed.Source.FALLBACK);
        assertThat(feed.message()).contains("대기오염도 호출 실패");
        assertThat(health.snapshot().get("fineDust").failureCount()).isEqualTo(1);
    }
}
