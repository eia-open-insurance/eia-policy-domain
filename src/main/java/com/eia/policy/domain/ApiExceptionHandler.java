package com.eia.policy.domain;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(error(ex.status().value(), ex.code(),
                ex.getMessage(), request, ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiException.FieldIssue> details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiException.FieldIssue(field.getField(), field.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error(400, "VALIDATION_ERROR",
                "Request validation failed", request, details));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex,
                                                        HttpServletRequest request) {
        String code = "X-EIA-Tenant".equalsIgnoreCase(ex.getHeaderName())
                ? "MISSING_TENANT_HEADER" : "MISSING_HEADER";
        return ResponseEntity.badRequest().body(error(400, code,
                ex.getHeaderName() + " header is required", request, List.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String code = "X-EIA-Tenant".equalsIgnoreCase(ex.getName())
                ? "INVALID_TENANT_HEADER" : "INVALID_PARAMETER";
        return ResponseEntity.badRequest().body(error(400, code,
                "Invalid value for " + ex.getName(), request, List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(400, "MALFORMED_REQUEST",
                "Request body is missing or malformed", request, List.of()));
    }

    private static ApiError error(int status, String code, String message, HttpServletRequest request,
                                  List<ApiException.FieldIssue> details) {
        return new ApiError(Instant.now(), status, code, message, request.getRequestURI(), details);
    }
}
