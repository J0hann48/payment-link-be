package com.kira.payment.paymentlinkbe.application.paymentlink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatePaymentLinkCommand(
        Long merchantId,
        Long recipientId,
        BigDecimal amount,
        String currency,
        String description,
        LocalDate expiresAt
) { }
