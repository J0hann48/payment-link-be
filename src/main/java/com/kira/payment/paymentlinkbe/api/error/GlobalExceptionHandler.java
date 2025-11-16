package com.kira.payment.paymentlinkbe.api.error;

import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.RecipientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({MerchantNotFoundException.class, RecipientNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(RuntimeException ex) {
        return new ErrorResponse(
                "NOT_FOUND",
                404,
                ex.getMessage()
        );
    }

    public record ErrorResponse(String status, int code, String message) {}
}
