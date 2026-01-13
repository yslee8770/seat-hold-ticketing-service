package com.example.ticket.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final HttpServletRequest request;

    public ApiExceptionHandler(HttpServletRequest request) {
        this.request = request;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
        String traceId = traceId(request);
        ErrorCode code = ex.getErrorCode();

        log.warn("[traceId={}] domain_error code={} msg={}", traceId, code.name(), ex.getMessage());

        return ResponseEntity
                .status(code.status())
                .body(ApiErrorResponse.of(code, ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String traceId = traceId(request);

        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());

        log.warn("[traceId={}] validation_error msg={}", traceId, msg);

        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_FAILED, msg, traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex) {
        String traceId = traceId(request);
        log.error("[traceId={}] unexpected_error", traceId, ex);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), traceId));
    }

    private String traceId(HttpServletRequest request) {
        Object v = request.getAttribute(TraceIdFilter.TRACE_ATTR);
        return (v instanceof String s && !s.isBlank()) ? s : "NO_TRACE_ID";
    }
}

