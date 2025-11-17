package com.kira.payment.paymentlinkbe.api.error;

import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkInvalidStateException;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.RecipientNotFoundException;
import com.kira.payment.paymentlinkbe.domain.psp.PspRoutingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.LocalDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.badRequest(
                "INVALID_INPUT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(PspRoutingException.class)
    public ResponseEntity<ApiErrorResponse> handlePspRouting(
            PspRoutingException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_GATEWAY,
                "PSP_ROUTING_FAILED",
                "We tried multiple PSPs and could not process your payment. Please try again later.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> {
                    String field = error.getField();
                    String defaultMessage = error.getDefaultMessage();
                    return "%s: %s".formatted(field, defaultMessage);
                })
                .orElse("Validation failed");

        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                message,
                "",
                LocalDateTime.now()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(PaymentLinkNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentLinkNotFound(
            PaymentLinkNotFoundException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND,
                "PAYMENT_LINK_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(PaymentLinkInvalidStateException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentLinkInvalidState(
            PaymentLinkInvalidStateException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.badRequest(
                "INVALID_PAYMENT_LINK_STATE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMerchantNotFound(MerchantNotFoundException ex) {
        ApiErrorResponse body = ApiErrorResponse.badRequest(
                "MERCHANT_NOT_FOUND",
                "Merchant %d not found".formatted(ex.getMerchantId()),
                ""
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RecipientNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMerchantNotFound(RecipientNotFoundException ex) {
        ApiErrorResponse body = ApiErrorResponse.badRequest(
                "MERCHANT_NOT_FOUND",
                "Merchant %d not found".formatted(ex.getRecipientId()),
                ""
        );
        return ResponseEntity.badRequest().body(body);
    }
}
