package com.kira.payment.paymentlinkbe.domain.psp;

public record PspChargeWebhookRequest( PspCode pspCode,
                                       String pspChargeId,
                                       String paymentId,
                                       ChargeStatus status,
                                       String failureCode,
                                       String failureMessage) {
}
