package com.selim.dashboard.domain;

/**
 * 단기예보 항목 하나 (category 단위 시점 값).
 */
public record WeatherPoint(
        String category,
        String label,
        String fcstDate,
        String fcstTime,
        Double value,
        String unit
) {
}
