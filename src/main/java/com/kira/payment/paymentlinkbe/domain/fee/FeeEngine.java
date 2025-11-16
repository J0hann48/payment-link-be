package com.kira.payment.paymentlinkbe.domain.fee;

import java.math.BigDecimal;

public interface FeeEngine {
    FeeBreakdown calculateForPaymentLink(
            Long merchantId,
            Long recipientId,
            BigDecimal amount,
            String currency
    );
}
