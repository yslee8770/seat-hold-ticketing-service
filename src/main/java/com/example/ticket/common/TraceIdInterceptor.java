package com.example.ticket.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String incoming = request.getHeader(TRACE_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            TraceContext.set(incoming);
        }
        String traceId = TraceContext.ensureTraceId();
        response.setHeader(TRACE_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TraceContext.clear();
    }
}

