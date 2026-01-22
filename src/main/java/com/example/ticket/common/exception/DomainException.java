package com.example.ticket.common.exception;

import com.example.ticket.common.ErrorCode;
import lombok.Getter;

@Getter
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }


    public DomainException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

}

