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
        return true; // 전체 적용
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
        // JSON이 아닌 응답은 건드리지 않음 (파일 다운로드/스트리밍 등)
        if (selectedContentType != null && !MediaType.APPLICATION_JSON.includes(selectedContentType)) {
            return body;
        }

        // ResponseEntity를 직접 리턴하는 컨트롤러가 섞여 있으면, body만 감싸고 싶을 수 있음
        // (보통은 컨트롤러에서 ResponseEntity를 쓰지 말고 DTO만 리턴하는 걸 추천)
        if (body instanceof ResponseEntity<?> re) {
            Object inner = re.getBody();
            if (inner instanceof ApiResponse<?> || inner instanceof ApiErrorResponse) {
                return body; // 이미 표준 포맷이면 그대로
            }
            return ResponseEntity.status(re.getStatusCode())
                    .headers(re.getHeaders())
                    .body(ApiResponse.of(inner, traceId()));
        }

        // 이미 표준 포맷이면 그대로
        if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) {
            return body;
        }

        // 정상 응답은 전부 래핑
        return ApiResponse.of(body, traceId());
    }

    private String traceId() {
        Object v = request.getAttribute(TraceIdFilter.TRACE_ATTR);
        return (v instanceof String s && !s.isBlank()) ? s : "NO_TRACE_ID";
    }
}
