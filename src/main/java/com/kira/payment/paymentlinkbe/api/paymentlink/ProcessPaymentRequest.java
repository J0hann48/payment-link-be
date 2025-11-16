package com.kira.payment.paymentlinkbe.api.paymentlink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessPaymentRequest(
        @NotBlank String pspToken,
        @Size(max = 32) String pspHint
) {
}
