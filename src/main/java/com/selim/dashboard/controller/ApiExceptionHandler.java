package com.selim.dashboard.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** 파라미터 검증 실패를 500 이 아니라 명확한 400 으로 돌려준다. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PARAMETER",
                "message", message.isBlank() ? "잘못된 요청 파라미터입니다" : message));
    }
}
