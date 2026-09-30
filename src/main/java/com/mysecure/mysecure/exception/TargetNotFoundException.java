package com.mysecure.mysecure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TargetNotFoundException extends RuntimeException {
    public TargetNotFoundException(String message) {
        super(message);
    }
}
