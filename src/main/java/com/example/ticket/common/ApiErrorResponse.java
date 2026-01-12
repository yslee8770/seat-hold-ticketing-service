package com.example.ticket.common;

public record ApiErrorResponse(
        String code,
        String message,
        String traceId
) {
    public static ApiErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ApiErrorResponse(code.name(), message, traceId);
    }
}

