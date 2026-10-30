package com.selim.dashboard.controller;

import com.selim.dashboard.client.Feed;
import com.selim.dashboard.client.UpstreamHealthRegistry;
import com.selim.dashboard.domain.FineDustReading;
import com.selim.dashboard.domain.SubwayCongestion;
import com.selim.dashboard.domain.WeatherPoint;
import com.selim.dashboard.dto.DashboardResponse;
import com.selim.dashboard.service.DashboardService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.summary();
    }

    @GetMapping("/weather")
    public Feed<WeatherPoint> weather() {
        return dashboardService.weather();
    }

    /**
     * @param realm 권역 코드(2자리). 환경부 권역 코드표를 따른다.
     */
    @GetMapping("/fine-dust")
    public Feed<FineDustReading> fineDust(
            @RequestParam(required = false) @Pattern(regexp = "\\d{2}", message = "realm 은 2자리 숫자 권역코드여야 합니다") String realm) {
        return dashboardService.fineDust(realm);
    }

    @GetMapping("/subway")
    public Feed<SubwayCongestion> subway(
            @RequestParam(required = false) @Size(max = 30, message = "stationName 은 30자 이하") String stationName) {
        return dashboardService.subway(stationName);
    }

    /** 캐시를 비우고 즉시 다시 불러온다. 데모/운영 확인용. */
    @PostMapping("/refresh")
    public DashboardResponse refresh() {
        dashboardService.evictAll();
        return dashboardService.summary();
    }

    /** 각 소스가 실데이터인지 목/대체 데이터인지 운영 확인용. */
    @GetMapping("/health/upstreams")
    public Map<String, UpstreamHealthRegistry.Snapshot> upstreams() {
        return dashboardService.upstreamHealth();
    }
}
