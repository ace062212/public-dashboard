package com.selim.dashboard.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * 공공데이터 오픈API 연결 설정.
 * 서비스 키는 환경 변수로만 주입하며, yml 에 평문으로 남기지 않는다.
 */
@ConfigurationProperties(prefix = "public-data")
public record PublicDataProperties(
        @DefaultValue("true") boolean mockWhenKeyMissing,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("60s") Duration cacheTtl,
        @DefaultValue("60s") Duration refreshInterval,
        @DefaultValue Weather weather,
        @DefaultValue FineDust fineDust,
        @DefaultValue Subway subway
) {

    public record Weather(
            @DefaultValue("http://apis.data.go.kr") String baseUrl,
            @DefaultValue("/1360000/GetVilageFcst/getVilageFcst") String path,
            @DefaultValue("") String serviceKey,
            @DefaultValue("60") int nx,
            @DefaultValue("127") int ny
    ) {
        public boolean configured() {
            return StringUtils.hasText(serviceKey);
        }
    }

    public record FineDust(
            @DefaultValue("http://openapi.epa.go.kr") String baseUrl,
            @DefaultValue("/main/service/realm/getTrend1DayRealTimeTierSR") String path,
            @DefaultValue("") String serviceKey,
            @DefaultValue("11") String realmCode
    ) {
        public boolean configured() {
            return StringUtils.hasText(serviceKey);
        }
    }

    public record Subway(
            @DefaultValue("http://smss.seoul.go.kr:9001") String baseUrl,
            @DefaultValue("/openapi2013/data/getCongestionStationDataInfoService/getCongestionStationDataInfo") String path,
            @DefaultValue("") String serviceKey,
            @DefaultValue("02") String lineId
    ) {
        public boolean configured() {
            return StringUtils.hasText(serviceKey);
        }
    }
}
