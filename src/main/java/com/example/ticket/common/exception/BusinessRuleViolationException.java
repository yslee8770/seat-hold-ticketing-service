package com.example.ticket.common.exception;

import com.example.ticket.common.ErrorCode;

public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(ErrorCode errorCode) {
        super(errorCode); // defaultMessage
    }
}