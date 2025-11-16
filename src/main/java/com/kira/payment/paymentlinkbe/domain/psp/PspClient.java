package com.kira.payment.paymentlinkbe.domain.psp;

import java.math.BigDecimal;

public interface PspClient{
    String code();

    PspChargeResult charge(String token, BigDecimal amount, String currency);
}
