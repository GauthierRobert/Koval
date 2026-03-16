package com.koval.trainingplannerbackend.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String error, String code, String message, String path) {
        return new ErrorResponse(status, error, code, message, path, Instant.now(), null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String code, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, code, message, path, Instant.now(), fieldErrors);
    }
}
