package com.kira.payment.paymentlinkbe.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePaymentLinkRequest(@NotNull Long merchantId,
                                       @NotNull BigDecimal amount,
                                       @NotBlank String currency,
                                       String description,
                                       LocalDate expiresAt,
                                       Long recipientId) {
}
