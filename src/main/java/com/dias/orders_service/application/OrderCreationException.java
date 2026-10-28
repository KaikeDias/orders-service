package com.dias.orders_service.application;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class OrderCreationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrderCreationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
