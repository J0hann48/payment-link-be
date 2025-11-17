package com.kira.payment.paymentlinkbe.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CardTokenizationException.class)
    public ResponseEntity<ApiErrorResponse> handleCardTokenization(
            CardTokenizationException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.badRequest(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .badRequest()
                .body(body);
    }

    public record ErrorResponse(String status, int code, String message) {}
}
