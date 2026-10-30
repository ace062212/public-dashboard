package com.selim.dashboard.client;

import com.selim.dashboard.config.PublicDataProperties;
import java.time.Duration;

/** 테스트용 설정 조립. 실제 키는 넣지 않는다. */
final class TestProps {

    private TestProps() {
    }

    static PublicDataProperties of(String weatherKey, String dustKey, String subwayKey) {
        return new PublicDataProperties(
                true,
                Duration.ofSeconds(3),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                new PublicDataProperties.Weather("http://weather.test", "/fcst", weatherKey, 60, 127),
                new PublicDataProperties.FineDust("http://dust.test", "/dust", dustKey, "11"),
                new PublicDataProperties.Subway("http://subway.test", "/subway", subwayKey, "02"));
    }
}
