package com.kira.payment.paymentlinkbe.api.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePaymentLinkCommand(Long merchantId,
                                       Long recipientId,
                                       BigDecimal amount,
                                       String currency,
                                       String description,
                                       LocalDate expiresAt) {
}
