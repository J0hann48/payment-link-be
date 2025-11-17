package com.kira.payment.paymentlinkbe.application.paymentlink;

public record ProcessPaymentCommand(
        String pspToken,
        String pspHint,
        String idempotencyKey
) {
}
