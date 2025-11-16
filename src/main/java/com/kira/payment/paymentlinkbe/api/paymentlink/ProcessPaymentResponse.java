package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.application.paymentlink.ProcessPaymentResult;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import java.math.BigDecimal;

public record ProcessPaymentResponse(
        Long paymentId,
        String paymentStatus,
        String pspUsed,
        String pspReference,
        BigDecimal amount,
        String currency,
        PaymentLinkResponse.FeePreviewResponse feeBreakdown,
        String paymentLinkSlug
) {
    public static ProcessPaymentResponse from(ProcessPaymentResult result) {
        return new ProcessPaymentResponse(
                result.paymentId(),
                result.paymentStatus().name(),
                result.pspUsed(),
                result.pspReference(),
                result.amount(),
                result.currency(),
                PaymentLinkResponse.FeePreviewResponse.from(result.feeBreakdown()),
                result.paymentLinkSlug()
        );
    }
}

