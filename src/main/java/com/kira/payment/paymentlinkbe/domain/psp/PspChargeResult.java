package com.kira.payment.paymentlinkbe.domain.psp;

import java.math.BigDecimal;

public record PspChargeResult(
        String pspChargeId,
        ChargeStatus status,
        BigDecimal amount,
        String currency,
        String failureCode,
        String failureMessage
) {

    public static PspChargeResult success(String pspChargeId, BigDecimal amount, String currency) {
        return new PspChargeResult(pspChargeId, ChargeStatus.SUCCEEDED, amount, currency, null, null);
    }

    public static PspChargeResult failure(String pspChargeId, String failureCode, String failureMessage) {
        return new PspChargeResult(pspChargeId, ChargeStatus.FAILED, null, null, failureCode, failureMessage);
    }
}
