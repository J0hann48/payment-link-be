package com.kira.payment.paymentlinkbe.api.error;

import com.kira.payment.paymentlinkbe.domain.psp.PspRoutingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ApiErrorResponse body = new ApiErrorResponse(
                "INVALID_INPUT",
                ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(PspRoutingException.class)
    public ResponseEntity<ApiErrorResponse> handlePspRouting(PspRoutingException ex) {
        ApiErrorResponse body = new ApiErrorResponse(
                "PSP_ROUTING_FAILED",
                "We tried multiple PSPs and could not process your payment. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
