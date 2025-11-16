package com.kira.payment.paymentlinkbe.domain.psp;

import java.math.BigDecimal;

public record PspChargeResult(String pspCode,
                              String pspReference,
                              boolean success,
                              String rawStatus,
                              BigDecimal amount,
                              String currency) {
}
