package com.kira.payment.paymentlinkbe.domain.psp;

import java.math.BigDecimal;

public interface PspClient{
    PspCode getCode();

    CardToken tokenizeCard(PspTokenizationRequest request);

    PspChargeResult charge(PspChargeRequest request);
}
