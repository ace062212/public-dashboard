package com.selim.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/** 실제 내장 서버로 요청을 보내 정적 페이지와 API 를 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("메인 화면이 text/html 로 서빙된다")
    void servesIndexPage() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(response.getBody()).contains("서울 실시간 대시보드");
    }

    @Test
    @DisplayName("스타일과 스크립트가 서빙된다")
    void servesAssets() {
        assertThat(rest.getForEntity("/styles.css", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/app.js", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("API 응답은 UTF-8 한글을 유지한다")
    void apiKeepsKoreanOverHttp() {
        ResponseEntity<String> response = rest.exchange("/api/weather", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("기온").contains("℃").contains("\"source\"");
    }

    @Test
    @DisplayName("한글 쿼리 파라미터를 그대로 받아 필터링한다")
    void acceptsKoreanQueryParamOverHttp() {
        URI uri = UriComponentsBuilder.fromUriString("/api/subway")
                .queryParam("stationName", "홍대입구")
                .encode()
                .build()
                .toUri();

        ResponseEntity<String> response = rest.exchange(uri, HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("홍대입구").doesNotContain("강남");
    }

    @Test
    @DisplayName("없는 주소는 404")
    void unknownPathIsNotFound() {
        assertThat(rest.getForEntity("/api/없는주소", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
