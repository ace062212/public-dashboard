package com.selim.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.selim.dashboard.client.Feed;
import com.selim.dashboard.client.FineDustClient;
import com.selim.dashboard.client.MockData;
import com.selim.dashboard.client.WeatherClient;
import com.selim.dashboard.domain.WeatherPoint;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DashboardCacheTest {

    @Autowired
    private DashboardService dashboardService;

    @MockitoBean
    private WeatherClient weatherClient;

    @MockitoBean
    private FineDustClient fineDustClient;

    @Test
    @DisplayName("캐시 덕분에 같은 요청의 공공API 호출은 한 번이다")
    void servesRepeatedCallsFromCache() {
        dashboardService.evictAll();
        given(weatherClient.fetch())
                .willReturn(Feed.live(List.of(new WeatherPoint("TMP", "기온", "20261030", "0900", 17.3, "℃"))));

        Feed<WeatherPoint> first = dashboardService.weather();
        Feed<WeatherPoint> second = dashboardService.weather();

        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        then(weatherClient).should(times(1)).fetch();
    }

    @Test
    @DisplayName("캐시를 비우면 다시 호출한다")
    void callsAgainAfterEvict() {
        dashboardService.evictAll();
        given(weatherClient.fetch())
                .willReturn(Feed.live(List.of(new WeatherPoint("TMP", "기온", "20261030", "0900", 17.3, "℃"))));

        dashboardService.weather();
        dashboardService.evictAll();
        dashboardService.weather();

        then(weatherClient).should(times(2)).fetch();
    }

    @Test
    @DisplayName("권역 코드가 다르면 캐시도 분리된다")
    void cachesPerRealmCode() {
        dashboardService.evictAll();
        given(fineDustClient.fetch("11")).willReturn(Feed.mock(MockData.fineDust()));
        given(fineDustClient.fetch("49")).willReturn(Feed.mock(MockData.fineDust()));

        dashboardService.fineDust("11");
        dashboardService.fineDust("11");
        dashboardService.fineDust("49");

        then(fineDustClient).should(times(1)).fetch("11");
        then(fineDustClient).should(times(1)).fetch("49");
    }
}
