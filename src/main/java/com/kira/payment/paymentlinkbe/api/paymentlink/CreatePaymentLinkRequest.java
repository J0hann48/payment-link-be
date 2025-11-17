package com.kira.payment.paymentlinkbe.api.paymentlink;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreatePaymentLinkRequest(
        @NotNull Long merchantId,
        Long recipientId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) String currency,
        @NotNull @Size(min = 1, max = 255) String description,
        @FutureOrPresent LocalDate expiresAt
) {
}
