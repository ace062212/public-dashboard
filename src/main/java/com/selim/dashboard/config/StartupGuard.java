package com.selim.dashboard.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 서비스 키 없는 상태로도 기동은 되되, 어떤 소스가 목 데이터인지 운영이 알 수 있도록 알린다.
 * mock-when-key-missing=false 이면 키 누락 시 기동을 실패시킨다 (추측성 기본값으로 실데이터 흉내를 내지 않도록).
 */
@Component
public class StartupGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);

    private final PublicDataProperties props;

    public StartupGuard(PublicDataProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missing = new ArrayList<>();
        if (!props.weather().configured()) {
            missing.add("weather(WEATHER_SERVICE_KEY)");
        }
        if (!props.fineDust().configured()) {
            missing.add("fineDust(FINE_DUST_SERVICE_KEY)");
        }
        if (!props.subway().configured()) {
            missing.add("subway(SUBWAY_SERVICE_KEY)");
        }

        if (missing.isEmpty()) {
            log.info("3개 공공API 서비스 키가 모두 설정되었습니다.");
            return;
        }
        if (props.mockWhenKeyMissing()) {
            log.warn("서비스 키 미설정으로 목 데이터를 반환합니다: {}", missing);
            return;
        }
        throw new IllegalStateException(
                "public-data.mock-when-key-missing=false 인데 서비스 키가 없습니다: " + missing
                        + " 환경 변수를 설정하거나 mock-when-key-missing=true 로 지정하세요.");
    }
}
