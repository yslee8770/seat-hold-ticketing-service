package com.example.ticket.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "User is blocked"),

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found"),
    EVENT_NOT_DRAFT(HttpStatus.CONFLICT, "Event not a draft"),
    EVENT_NOT_OPEN(HttpStatus.CONFLICT, "Event not a open"),
    EVENT_NOT_ON_SALE(HttpStatus.CONFLICT, "Event not on sale"),
    INVALID_SEAT_SET(HttpStatus.BAD_REQUEST, "Invalid seat set"),
    HOLD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Hold limit exceeded"),
    SEAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Seat not available"),
    DUPLICATED_SEAT_IN_REQUEST(HttpStatus.CONFLICT, "duplicated seat information in request"),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "Hold expired"),
    HOLD_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Hold token not found"),
    BOOKING_ALREADY_SAVED(HttpStatus.CONFLICT, "booking already saved"),
    BOOKING_ITEM_ALREADY_SAVED(HttpStatus.CONFLICT, "booking item already saved"),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "booking not found"),
    USER_NOT_MATCHED(HttpStatus.CONFLICT, "booking userId is not equals with userid"),


    CONFIRM_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "confirm idempotency conflict"),
    PAYMENT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Payment idempotency conflict"),
    PAYMENT_DECLINED(HttpStatus.CONFLICT, "Payment declined"),
    PAYMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Payment timeout"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency conflict"),
    AMOUNT_MISMATCH(HttpStatus.CONFLICT, "Amount mismatch"),
    INVALID_EVENT_SALES_WINDOW(HttpStatus.BAD_REQUEST, "Invalid sales window"),

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),

    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT,  "Request is in progress");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public String defaultMessage() { return defaultMessage; }
}
