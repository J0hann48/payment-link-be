package com.kira.payment.paymentlinkbe.application.paymentlink;

public class PaymentLinkInvalidStateException extends RuntimeException {

    public PaymentLinkInvalidStateException(String message) {
        super(message);
    }
}
