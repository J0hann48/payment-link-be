package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import java.math.BigDecimal;

public record FeeBreakdownResponse(
        BigDecimal baseAmount,
        BigDecimal processingFee,
        BigDecimal fxFee,
        BigDecimal incentiveDiscount,
        BigDecimal totalFees,
        BigDecimal finalAmount,
        String currency
) {

    public static FeeBreakdownResponse from(FeeBreakdown fb) {
        if (fb == null) {
            return null;
        }
        return new FeeBreakdownResponse(
                fb.baseAmount(),
                fb.processingFee(),
                fb.fxFee(),
                fb.incentiveDiscount(),
                fb.totalFees(),
                fb.finalAmount(),
                fb.currency()
        );
    }
}
