package com.selim.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.selim.dashboard.config.PublicDataProperties;
import com.selim.dashboard.domain.WeatherPoint;
import java.time.Duration;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeatherClientTest {

    private static final String OK_ITEM = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
             "body":{"items":{"item":[{"category":"TMP","fcstDate":"20261030","fcstTime":"0900","fcstValue":"17.3"}]}}}}""";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private UpstreamHealthRegistry health;
    private WeatherClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        health = new UpstreamHealthRegistry();
        client = new WeatherClient(builder, TestProps.of("secret-key", "dust-key", "subway-key"), health);
    }

    static PublicDataProperties props(String weatherKey, String dustKey, String subwayKey) {
        return TestProps.of(weatherKey, dustKey, subwayKey);
    }

    @Test
    @DisplayName("서비스 키가 없으면 호출하지 않고 목 데이터를 반환한다")
    void returnsMockWithoutKey() {
        WeatherClient keyless = new WeatherClient(RestClient.builder(), TestProps.of("", "", ""), health);

        Feed<WeatherPoint> feed = keyless.fetch();

        assertThat(feed.source()).isEqualTo(Feed.Source.MOCK);
        assertThat(feed.items()).isNotEmpty();
        assertThat(health.snapshot().get("weather").status()).isEqualTo("MOCK");
        server.verify();
    }

    @Test
    @DisplayName("예보 항목을 라벨과 단위가 붙은 값으로 바꾼다")
    void parsesForecastItems() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"items":{"item":[
                   {"category":"TMP","fcstDate":"20261030","fcstTime":"0900","fcstValue":"17.3"},
                   {"category":"SKY","fcstDate":"20261030","fcstTime":"0900","fcstValue":"3"},
                   {"category":"TMP","fcstDate":"20261030","fcstTime":"1100","fcstValue":"19.8"},
                   {"category":"UNKNOWN","fcstDate":"20261030","fcstTime":"1100","fcstValue":"1"}
                 ]}}}}""";
        server.expect(requestTo(Matchers.containsString("/fcst")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<WeatherPoint> feed = client.fetch();

        assertThat(feed.source()).isEqualTo(Feed.Source.LIVE);
        assertThat(feed.items()).hasSize(3);
        assertThat(feed.items()).extracting(WeatherPoint::category).containsExactly("TMP", "SKY", "TMP");
        assertThat(feed.items().get(0).value()).isEqualTo(17.3);
        assertThat(feed.items().get(0).label()).isEqualTo("기온");
        assertThat(feed.items().get(0).unit()).isEqualTo("℃");
        assertThat(feed.items().get(1).label()).isEqualTo("하늘상태");
        assertThat(health.snapshot().get("weather").status()).isEqualTo("LIVE");
        server.verify();
    }

    @Test
    @DisplayName("요청 파라미터가 그대로 도착하고 쿼리스트링은 한 번만 인코딩된다")
    void sendsParametersSingleEncoded() {
        server.expect(requestTo(Matchers.containsString("/fcst")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("serviceKey", "secret-key"))
                .andExpect(queryParam("nx", "60"))
                .andExpect(queryParam("ny", "127"))
                .andExpect(queryParam("type", "json"))
                .andExpect(queryParam("base_time",
                        Matchers.in(List.of("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"))))
                .andExpect(request -> assertThat(request.getURI().getRawQuery()).doesNotContain("%25"))
                .andRespond(withSuccess(OK_ITEM, MediaType.APPLICATION_JSON));

        assertThat(client.fetch().source()).isEqualTo(Feed.Source.LIVE);
        server.verify();
    }

    @Test
    @DisplayName("업스트림이 500 이면 목 데이터로 대체하고 실패를 기록한다")
    void degradesOnServerError() {
        server.expect(requestTo(Matchers.containsString("/fcst")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Feed<WeatherPoint> feed = client.fetch();

        assertThat(feed.source()).isEqualTo(Feed.Source.FALLBACK);
        assertThat(feed.message()).contains("기상청 호출 실패");
        assertThat(feed.items()).isNotEmpty();
        UpstreamHealthRegistry.Snapshot snapshot = health.snapshot().get("weather");
        assertThat(snapshot.status()).isEqualTo("FALLBACK");
        assertThat(snapshot.failureCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("헤더 resultCode 가 비정상이면 장애로 간주한다")
    void treatsErrorHeaderAsFailure() {
        String json = """
                {"response":{"header":{"resultCode":"22","resultMsg":"서비스키 오류입니다"},"body":{}}}""";
        server.expect(requestTo(Matchers.containsString("/fcst")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Feed<WeatherPoint> feed = client.fetch();

        assertThat(feed.source()).isEqualTo(Feed.Source.FALLBACK);
        assertThat(feed.message()).contains("서비스키 오류");
    }

    @Test
    @DisplayName("항목이 비어 있으면 장애로 간주한다")
    void treatsEmptyItemsAsFailure() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"ok"},"body":{"items":{"item":[]}}}}""";
        server.expect(requestTo(Matchers.containsString("/fcst")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.fetch().source()).isEqualTo(Feed.Source.FALLBACK);
    }
}
