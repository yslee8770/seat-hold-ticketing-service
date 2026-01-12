package com.example.ticket.common;

import java.util.UUID;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {}

    public static String ensureTraceId() {
        String cur = TRACE_ID.get();
        if (cur != null && !cur.isBlank()) return cur;

        String created = UUID.randomUUID().toString().replace("-", "");
        TRACE_ID.set(created);
        return created;
    }

    public static String getOrNull() {
        return TRACE_ID.get();
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) return;
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}

