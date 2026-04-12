package com.carpool.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard envelope for all API responses.
 * Every endpoint returns this — success and error alike.
 *
 * Success:  { "success": true,  "data": {...},  "timestamp": "..." }
 * Error:    { "success": false, "error": {...}, "timestamp": "..." }
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, ApiError error) {
        this.success   = success;
        this.data      = data;
        this.error     = error;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
