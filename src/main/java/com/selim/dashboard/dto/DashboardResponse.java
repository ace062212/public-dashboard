package com.selim.dashboard.dto;

import com.selim.dashboard.client.Feed;
import com.selim.dashboard.domain.FineDustReading;
import com.selim.dashboard.domain.SubwayCongestion;
import com.selim.dashboard.domain.WeatherPoint;
import java.time.Instant;

/** 메인 화면 한 번의 렌더에 필요한 데이터 묶음. */
public record DashboardResponse(
        Instant generatedAt,
        Feed<WeatherPoint> weather,
        Feed<FineDustReading> fineDust,
        Feed<SubwayCongestion> subway,
        WeatherHeadline headline
) {
}
