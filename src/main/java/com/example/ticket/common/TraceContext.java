package com.example.ticket.common;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {}

    public static String getOrNull() {
        return TRACE_ID.get();
    }

    public static String require() {
        String cur = TRACE_ID.get();
        if (cur == null || cur.isBlank()) {
            throw new IllegalStateException("TraceId is not set. TraceIdFilter must run first.");
        }
        return cur;
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) return;
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}

