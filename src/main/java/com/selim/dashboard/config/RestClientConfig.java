package com.selim.dashboard.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * 공공API 호출용 RestClient. 타임아웃을 명시하지 않으면 업스트림 지연 시 스레드가 막힌다.
     */
    @Bean
    public RestClient.Builder publicApiRestClientBuilder(PublicDataProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "public-dashboard/0.1");
    }
}
