package com.selim.dashboard.domain;

/**
 * 지하철 역사 혼잡도 하나.
 */
public record SubwayCongestion(
        String lineId,
        String lineName,
        String stationId,
        String stationName,
        Double congestionRate,
        String level,
        String trainNo,
        String updatedAt
) {
}
