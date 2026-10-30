package com.selim.dashboard.service;

import com.selim.dashboard.dto.DashboardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 캐시가 만료된 뒤에야 호출이 일어나면 그 순간 화면이 느려진다.
 * 주기적으로 미리 갱신해서 항상 캐시된 값이 나가도록 한다.
 */
@Component
public class RefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshScheduler.class);

    private final DashboardService dashboardService;

    public RefreshScheduler(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Scheduled(initialDelayString = "${public-data.refresh-interval:60s}",
            fixedDelayString = "${public-data.refresh-interval:60s}")
    public void refresh() {
        try {
            dashboardService.evictAll();
            DashboardResponse summary = dashboardService.summary();
            log.debug("백그라운드 갱신 완료: weather={}, fineDust={}, subway={}",
                    summary.weather().source(), summary.fineDust().source(), summary.subway().source());
        } catch (RuntimeException e) {
            log.warn("백그라운드 갱신 실패: {}", e.getMessage());
        }
    }
}
