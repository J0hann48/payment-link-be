package com.kira.payment.paymentlinkbe.api.psp;

import java.math.BigDecimal;

public record StripeWebhookRequest(
        String eventType,
        String pspReference,
        BigDecimal amount,
        String currency,
        String status
) {
}
