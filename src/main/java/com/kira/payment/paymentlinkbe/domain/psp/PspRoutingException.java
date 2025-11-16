package com.kira.payment.paymentlinkbe.domain.psp;

public class PspRoutingException extends RuntimeException {
    public PspRoutingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PspRoutingException(String message) {
        super(message);
    }
}
