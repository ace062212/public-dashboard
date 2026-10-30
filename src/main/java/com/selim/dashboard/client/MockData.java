package com.selim.dashboard.client;

import com.selim.dashboard.domain.FineDustReading;
import com.selim.dashboard.domain.Grades;
import com.selim.dashboard.domain.SubwayCongestion;
import com.selim.dashboard.domain.WeatherPoint;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 서비스 키 없이도 화면과 테스트가 돌아가도록 하는 고정 값.
 * 실제 값처럼 보이기보다 "목"임을 명확히 보여주는 규칙적인 수열을 쓴다.
 */
public final class MockData {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private MockData() {
    }

    public static List<WeatherPoint> weather() {
        LocalDate today = LocalDate.now(SEOUL);
        String date = today.format(DATE);
        double[] temp = {11.0, 12.5, 14.0, 15.5, 14.0, 12.0};
        double[] pop = {10, 20, 30, 30, 20, 10};
        double[] reh = {55, 50, 45, 42, 50, 60};
        double[] feel = {9.5, 11.0, 13.0, 14.5, 13.0, 10.5};
        String[] sky = {"1", "1", "3", "3", "4", "3"};

        List<WeatherPoint> points = new ArrayList<>();
        for (int i = 0; i < temp.length; i++) {
            String time = String.format("%02d00", (i * 3 + 2) % 24);
            points.add(new WeatherPoint("TMP", "기온", date, time, temp[i], "℃"));
            points.add(new WeatherPoint("POP", "강수확률", date, time, pop[i], "%"));
            points.add(new WeatherPoint("REH", "습도", date, time, reh[i], "%"));
            points.add(new WeatherPoint("TMP_FEEL", "체감온도", date, time, feel[i], "℃"));
            points.add(new WeatherPoint("SKY", "하늘상태", date, time, Double.parseDouble(sky[i]), ""));
        }
        return points;
    }

    public static List<FineDustReading> fineDust() {
        record Region(String name, double pm10, double pm25) {
        }
        List<Region> regions = List.of(
                new Region("서울", 44, 24),
                new Region("인천", 52, 31),
                new Region("경기", 47, 27),
                new Region("충북", 66, 41),
                new Region("대전", 58, 35),
                new Region("광주", 39, 21),
                new Region("부산", 35, 19),
                new Region("강원", 41, 22),
                new Region("제주", 22, 11));

        String measuredAt = LocalDate.now(SEOUL).format(DATE);
        List<FineDustReading> readings = new ArrayList<>();
        for (Region region : regions) {
            readings.add(new FineDustReading(region.name(), "PM10", "미세먼지",
                    region.pm10(), Grades.pm10(region.pm10()), measuredAt));
            readings.add(new FineDustReading(region.name(), "PM25", "초미세먼지",
                    region.pm25(), Grades.pm25(region.pm25()), measuredAt));
        }
        return readings;
    }

    public static List<SubwayCongestion> subway() {
        record Station(String id, String name, double rate) {
        }
        List<Station> stations = List.of(
                new Station("0202", "교대", 118),
                new Station("0203", "강남", 164),
                new Station("0206", "사당", 141),
                new Station("0208", "잠실", 96),
                new Station("0212", "왕십리", 88),
                new Station("0216", "신촌", 103),
                new Station("0219", "홍대입구", 132),
                new Station("0221", "신림", 77),
                new Station("0231", "성수", 69));

        String updatedAt = java.time.LocalDateTime.now(SEOUL)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        List<SubwayCongestion> result = new ArrayList<>();
        for (Station station : stations) {
            result.add(new SubwayCongestion("02", "2호선", station.id(), station.name(),
                    station.rate(), Grades.congestion(station.rate()), "5", updatedAt));
        }
        return result;
    }
}
