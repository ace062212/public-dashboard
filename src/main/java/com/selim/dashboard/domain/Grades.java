package com.selim.dashboard.domain;

/**
 * 환경부 통합대기환경지정 기준의 등급 임계값. 업스트림이 등급을 주지 않는 서비스도 있어 로컬에서 계산한다.
 */
public final class Grades {

    private Grades() {
    }

    public static String pm10(Double value) {
        return band(value, 30, 80, 150);
    }

    public static String pm25(Double value) {
        return band(value, 15, 35, 75);
    }

    public static String ozone(Double value) {
        return band(value, 0.03, 0.1, 0.2);
    }

    public static String nitrogenDioxide(Double value) {
        return band(value, 0.03, 0.06, 0.1);
    }

    /** 임계값 없는 항목용. 값이 없으면 등급도 없다. */
    public static String none(Double value) {
        return value == null ? null : "정보없음";
    }

    /** 지하철 혼잡도 등급. */
    public static String congestion(Double rate) {
        if (rate == null) {
            return null;
        }
        if (rate < 80) {
            return "여유";
        }
        if (rate < 130) {
            return "보통";
        }
        if (rate < 150) {
            return "혼잡";
        }
        return "매우혼잡";
    }

    private static String band(Double value, double good, double normal, double bad) {
        if (value == null) {
            return null;
        }
        if (value <= good) {
            return "좋음";
        }
        if (value <= normal) {
            return "보통";
        }
        if (value <= bad) {
            return "나쁨";
        }
        return "매우나쁨";
    }
}
