package com.selim.dashboard.dto;

/** 날씨 카드 상단에 크게 보이는 요약 값. */
public record WeatherHeadline(
        Double temperature,
        Double feelsLike,
        Double precipitationProbability,
        String skyLabel,
        String fcstDate,
        String fcstTime
) {
}
