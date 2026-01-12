package com.example.ticket.common;

public class BusinessRuleViolationException extends DomainException {
    public BusinessRuleViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}