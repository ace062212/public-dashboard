package com.selim.dashboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradesTest {

    @ParameterizedTest
    @CsvSource({
            "30, 좋음",
            "31, 보통",
            "80, 보통",
            "81, 나쁨",
            "150, 나쁨",
            "151, 매우나쁨"
    })
    @DisplayName("PM10 등급은 환경부 임계값 경계를 정확히 지킨다")
    void pm10Boundaries(double value, String expected) {
        assertThat(Grades.pm10(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "15, 좋음",
            "16, 보통",
            "35, 보통",
            "36, 나쁨",
            "75, 나쁨",
            "76, 매우나쁨"
    })
    @DisplayName("PM25 등급은 환경부 임계값 경계를 정확히 지킨다")
    void pm25Boundaries(double value, String expected) {
        assertThat(Grades.pm25(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("값이 없으면 등급도 없다")
    void nullValueHasNoGrade() {
        assertThat(Grades.pm10(null)).isNull();
        assertThat(Grades.pm25(null)).isNull();
        assertThat(Grades.congestion(null)).isNull();
        assertThat(Grades.none(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "79.9, 여유",
            "80, 보통",
            "129, 보통",
            "130, 혼잡",
            "149, 혼잡",
            "150, 매우혼잡"
    })
    @DisplayName("혼잡도 등급 경계를 확인한다")
    void congestionBoundaries(double rate, String expected) {
        assertThat(Grades.congestion(rate)).isEqualTo(expected);
    }
}
