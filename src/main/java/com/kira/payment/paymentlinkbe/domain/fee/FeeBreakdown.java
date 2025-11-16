package com.kira.payment.paymentlinkbe.domain.fee;

import java.math.BigDecimal;

public record FeeBreakdown(
        BigDecimal baseAmount,
        BigDecimal processingFee,
        BigDecimal fxFee,
        BigDecimal incentiveDiscount,
        BigDecimal totalFees,
        BigDecimal finalAmount,
        String currency
) {
}
