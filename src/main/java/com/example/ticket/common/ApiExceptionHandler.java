package com.example.ticket.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
        String traceId = TraceContext.ensureTraceId();
        log.warn("[traceId={}] domain_error code={} msg={}", traceId, ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex) {
        String traceId = TraceContext.ensureTraceId();
        log.error("[traceId={}] unexpected_error", traceId, ex);
        return ResponseEntity
                .internalServerError()
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Internal error", traceId));
    }
}

