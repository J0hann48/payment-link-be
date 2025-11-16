package com.kira.payment.paymentlinkbe.domain.psp;

public interface PspWebhookPublisher {
    void publishChargeSucceeded(
            PspCode pspCode,
            String pspChargeId,
            String paymentLinkId
    );

    void publishChargeFailed(
            PspCode pspCode,
            String pspChargeId,
            String paymentLinkId,
            String failureCode,
            String failureMessage
    );
}
