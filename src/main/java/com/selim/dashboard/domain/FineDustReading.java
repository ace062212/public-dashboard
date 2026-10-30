package com.selim.dashboard.domain;

/**
 * 권역별 대기오염물질 실측 값 하나.
 */
public record FineDustReading(
        String regionName,
        String itemName,
        String itemLabel,
        Double value,
        String grade,
        String measuredAt
) {
}
