package com.carpool.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Structured error payload included in ApiResponse on failures.
 * 'fieldErrors' is only populated for validation errors (400).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String code;       // e.g. "RIDE_NOT_FOUND", "VALIDATION_ERROR"
    private final String message;    // human-readable
    private final Map<String, String> fieldErrors; // field -> error message, for 400s

    public static ApiError of(String code, String message) {
        return ApiError.builder()
                .code(code)
                .message(message)
                .build();
    }

    public static ApiError withFields(String code, String message,
                                      Map<String, String> fieldErrors) {
        return ApiError.builder()
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors)
                .build();
    }
}
