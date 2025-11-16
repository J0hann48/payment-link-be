package com.kira.payment.paymentlinkbe.application.paymentlink;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;

import java.math.BigDecimal;

public record ProcessPaymentResult(
        Long paymentId,
        PaymentStatus paymentStatus,
        String pspUsed,
        String pspReference,
        BigDecimal amount,
        String currency,
        FeeBreakdown feeBreakdown,
        String paymentLinkSlug
) {
    public static ProcessPaymentResult from(
            Payment payment,
            FeeBreakdown feeBreakdown,
            String pspUsed
    ) {
        return new ProcessPaymentResult(
                payment.getId(),
                payment.getStatus(),
                pspUsed,
                payment.getPspReference(),
                payment.getAmount(),
                payment.getCurrency(),
                feeBreakdown,
                payment.getPaymentLink().getSlug()
        );
    }
}
