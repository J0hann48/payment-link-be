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

    public static FeeBreakdownResponse from(FeeBreakdown feeBreakdown) {
        if (feeBreakdown == null) {
            return null;
        }
        return new FeeBreakdownResponse(
                feeBreakdown.baseAmount(),
                feeBreakdown.processingFee(),
                feeBreakdown.fxFee(),
                feeBreakdown.incentiveDiscount(),
                feeBreakdown.totalFees(),
                feeBreakdown.finalAmount(),
                feeBreakdown.currency()
        );
    }
}
