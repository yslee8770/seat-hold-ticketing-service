package com.example.ticket.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final HttpServletRequest request;

    public ApiResponseAdvice(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest req,
            ServerHttpResponse res
    ) {
        if (selectedContentType != null && !MediaType.APPLICATION_JSON.includes(selectedContentType)) {
            return body;
        }

        if (body instanceof ResponseEntity<?> re) {
            Object inner = re.getBody();
            if (inner instanceof ApiResponse<?> || inner instanceof ApiErrorResponse) {
                return body;
            }
            return ResponseEntity.status(re.getStatusCode())
                    .headers(re.getHeaders())
                    .body(ApiResponse.of(inner, traceId()));
        }

        if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) {
            return body;
        }

        return ApiResponse.of(body, traceId());
    }

    private String traceId() {
        Object v = request.getAttribute(TraceIdFilter.TRACE_ATTR);
        return (v instanceof String s && !s.isBlank()) ? s : "NO_TRACE_ID";
    }
}
