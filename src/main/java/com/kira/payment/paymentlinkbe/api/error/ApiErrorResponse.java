package com.kira.payment.paymentlinkbe.api.error;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        LocalDateTime timestamp
) {

    public static ApiErrorResponse badRequest(String code, String message, String path) {
        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                code,
                message,
                path,
                LocalDateTime.now()
        );
    }

    public static ApiErrorResponse of(HttpStatus status, String code, String message, String path) {
        return new ApiErrorResponse(
                status.value(),
                code,
                message,
                path,
                LocalDateTime.now()
        );
    }
}
