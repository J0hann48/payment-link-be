package com.kira.payment.paymentlinkbe.api.psp;

import java.math.BigDecimal;

public record AdyenWebhookRequest(String eventCode,
                                  String success,
                                  String pspReference,
                                  BigDecimal amount,
                                  String currency) {
}
