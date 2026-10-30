package com.selim.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공API 마다 items 감싸는 구조와 필드명이 제각각이라 바인딩 DTO 대신 트리로 읽고
 * 후보 필드명 중에서 집어오는 헬퍼를 쓴다. 새 서비스가 추가돼도 클라이언트 수정이 적다.
 */
public final class JsonNodes {

    private JsonNodes() {
    }

    public static List<JsonNode> extractItems(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        JsonNode body = root.path("response").path("body");
        if (body.isMissingNode() || body.isNull()) {
            body = root;
        }
        JsonNode items = body.path("items");
        if (items.isMissingNode() || items.isNull()) {
            items = body;
        }
        JsonNode array = items.isArray() ? items : items.path("item");
        if (array.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            array.forEach(list::add);
            return list;
        }
        if (array.isObject()) {
            return List.of(array);
        }
        return List.of();
    }

    /** 헤더 resultCode 가 정상인지. 헤더가 없는 서비스는 통과시킨다. */
    public static boolean isSuccessHeader(JsonNode root) {
        if (root == null) {
            return false;
        }
        JsonNode code = root.path("response").path("header").path("resultCode");
        if (code.isMissingNode() || code.isNull() || code.asText().isBlank()) {
            return true;
        }
        String value = code.asText().trim();
        return "00".equals(value) || "03".equals(value) || "SUCCESS".equalsIgnoreCase(value);
    }

    public static String errorMessage(JsonNode root) {
        if (root == null) {
            return "응답 없음";
        }
        String msg = root.path("response").path("header").path("resultMsg").asText("");
        if (msg.isBlank()) {
            msg = root.path("msg").asText("");
        }
        if (msg.isBlank()) {
            msg = root.path("response").path("header").path("resultCode").asText("");
        }
        return msg.isBlank() ? "응답 형식 인식 불가" : msg;
    }

    /** 후보 필드명 중 처음 발견된 텍스트 값. 없으면 null. */
    public static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    public static BigDecimal decimal(JsonNode node, String... names) {
        String text = text(node, names);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
