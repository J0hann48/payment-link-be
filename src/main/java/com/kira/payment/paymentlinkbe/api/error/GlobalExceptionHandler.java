package com.kira.payment.paymentlinkbe.api.error;

import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.RecipientNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
