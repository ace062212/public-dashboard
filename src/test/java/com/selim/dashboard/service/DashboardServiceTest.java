package com.selim.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.selim.dashboard.domain.WeatherPoint;
import com.selim.dashboard.dto.WeatherHeadline;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DashboardServiceTest {

    private static WeatherPoint point(String category, String date, String time, Double value) {
        return new WeatherPoint(category, category, date, time, value, "");
    }

    @Test
    @DisplayName("요약은 가장 이른 시점의 값을 고른다")
    void headlinePicksEarliestForecast() {
        List<WeatherPoint> items = List.of(
                point("TMP", "20261030", "1400", 21.5),
                point("TMP", "20261030", "0900", 17.3),
                point("TMP_FEEL", "20261030", "1400", 20.0),
                point("TMP_FEEL", "20261030", "0900", 15.1),
                point("POP", "20261030", "1400", 60.0),
                point("POP", "20261030", "0900", 10.0),
                point("SKY", "20261030", "1400", 4.0),
                point("SKY", "20261030", "0900", 1.0));

        WeatherHeadline headline = DashboardService.headline(items);

        assertThat(headline.temperature()).isEqualTo(17.3);
        assertThat(headline.feelsLike()).isEqualTo(15.1);
        assertThat(headline.precipitationProbability()).isEqualTo(10.0);
        assertThat(headline.skyLabel()).isEqualTo("맑음");
        assertThat(headline.fcstTime()).isEqualTo("0900");
    }

    @Test
    @DisplayName("값이 없는 항목은 요약에서 제외한다")
    void headlineSkipsNullValues() {
        List<WeatherPoint> items = List.of(
                point("TMP", "20261030", "0900", null),
                point("TMP", "20261030", "1100", 19.8));

        assertThat(DashboardService.headline(items).temperature()).isEqualTo(19.8);
    }

    @Test
    @DisplayName("항목이 없으면 요약도 비어 있다")
    void headlineWithNoItems() {
        WeatherHeadline headline = DashboardService.headline(List.of());

        assertThat(headline.temperature()).isNull();
        assertThat(headline.skyLabel()).isEqualTo("정보 없음");
    }

    @ParameterizedTest
    @CsvSource({
            "1.0, 맑음",
            "2.0, 구름많음",
            "3.0, 구름많음",
            "4.0, 흐림",
            "9.0, '기타(9)'"
    })
    @DisplayName("하늘상태 코드를 문구로 바꾼다")
    void skyLabels(double code, String expected) {
        assertThat(DashboardService.skyLabel(code)).isEqualTo(expected);
    }
}
