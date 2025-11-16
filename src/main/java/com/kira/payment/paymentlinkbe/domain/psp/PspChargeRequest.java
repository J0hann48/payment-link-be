package com.kira.payment.paymentlinkbe.domain.psp;

import java.math.BigDecimal;

public record PspChargeRequest(String cardToken,
                               BigDecimal amount,
                               String currency) {
}
