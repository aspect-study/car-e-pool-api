package com.carpool.web.config;

import com.carpool.common.exception.CarpoolException;
import com.carpool.common.response.ApiError;
import com.carpool.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central exception handler.
 * All controllers throw exceptions — this handler converts them to
 * consistent ApiResponse envelopes without try-catch in controllers.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles all domain exceptions (RideFullException, BookingNotFoundException, etc.)
     * The exception carries its own HTTP status and error code.
     */
    @ExceptionHandler(CarpoolException.class)
    public ResponseEntity<ApiResponse<Void>> handleCarpoolException(CarpoolException ex) {
        log.warn("Domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.fail(ApiError.of(ex.getErrorCode(), ex.getMessage())));
    }

    /**
     * Handles @Valid / @Validated failures on request bodies.
     * Returns field-level error map for client-side form validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        // Keep first error per field if multiple violations
                        (existing, replacement) -> existing
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ApiError.withFields("VALIDATION_ERROR", "Request validation failed", fieldErrors)
                ));
    }

    /**
     * Catch-all for unexpected errors.
     * Logs the full stack trace but returns a generic message to the client
     * — never leak internal details to API consumers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        ApiError.of("INTERNAL_ERROR", "An unexpected error occurred.")
                ));
    }
}
