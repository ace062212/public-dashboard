package com.selim.dashboard.controller;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("메인 화면 데이터는 목 모드라도 200 과 세 소스를 함께 돌려준다")
    void dashboardReturnsAllFeeds() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.weather.source", is(oneOf("MOCK", "LIVE", "FALLBACK"))))
                .andExpect(jsonPath("$.weather.items", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.fineDust.items", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.subway.items", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.headline.temperature", notNullValue()))
                .andExpect(jsonPath("$.headline.skyLabel", notNullValue()));
    }

    @Test
    @DisplayName("날씨 항목은 한글 라벨과 단위를 잃지 않는다")
    void weatherKeepsKoreanLabels() throws Exception {
        mockMvc.perform(get("/api/weather").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category", is("TMP")))
                .andExpect(jsonPath("$.items[0].label", is("기온")))
                .andExpect(jsonPath("$.items[0].unit", is("℃")))
                .andExpect(jsonPath("$.items[?(@.label == '하늘상태')]", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("미세먼지는 권역 코드를 지정할 수 있다")
    void fineDustAcceptsRealm() throws Exception {
        mockMvc.perform(get("/api/fine-dust").param("realm", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.items[0].grade", notNullValue()));
    }

    @Test
    @DisplayName("잘못된 권역 코드는 400 과 함께 거부한다")
    void rejectsBadRealmCode() throws Exception {
        mockMvc.perform(get("/api/fine-dust").param("realm", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("권역 코드가 없거나 길어도 400 이다")
    void rejectsWrongLengthRealm() throws Exception {
        mockMvc.perform(get("/api/fine-dust").param("realm", "111"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_PARAMETER")));
    }

    @Test
    @DisplayName("한글 역 이름으로 혼잡도를 좁힐 수 있다")
    void filtersSubwayByKoreanStationName() throws Exception {
        mockMvc.perform(get("/api/subway").param("stationName", "강남"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].stationName", is("강남")))
                .andExpect(jsonPath("$.items[0].level", notNullValue()));
    }

    @Test
    @DisplayName("존재하지 않는 역 이름이면 빈 목록을 돌려준다")
    void returnsEmptyForUnknownStation() throws Exception {
        mockMvc.perform(get("/api/subway").param("stationName", "없는역이름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("역 이름이 30자를 넘으면 400 이다")
    void rejectsTooLongStationName() throws Exception {
        mockMvc.perform(get("/api/subway").param("stationName", "가".repeat(31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_PARAMETER")));
    }

    @Test
    @DisplayName("강제 갱신은 새로고침과 같은 응답을 준다")
    void forceRefreshReturnsSummary() throws Exception {
        mockMvc.perform(post("/api/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt", notNullValue()))
                .andExpect(jsonPath("$.subway.items", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("소스별 상태를 확인할 수 있다")
    void exposesUpstreamHealth() throws Exception {
        mockMvc.perform(get("/api/health/upstreams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather.status", notNullValue()))
                .andExpect(jsonPath("$.fineDust.status", notNullValue()))
                .andExpect(jsonPath("$.subway.status", notNullValue()))
                .andExpect(jsonPath("$.weather.successCount", notNullValue()));
    }

    @Test
    @DisplayName("없는 API 경로는 404")
    void unknownApiPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("허용되지 않은 메서드는 405")
    void wrongMethodIsNotAllowed() throws Exception {
        mockMvc.perform(post("/api/dashboard"))
                .andExpect(status().isMethodNotAllowed());
    }
}
