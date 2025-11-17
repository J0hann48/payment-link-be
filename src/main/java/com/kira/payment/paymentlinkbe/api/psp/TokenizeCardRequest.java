package com.kira.payment.paymentlinkbe.api.psp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TokenizeCardRequest(@NotBlank String cardNumber,
                                  @NotNull @Positive Integer expMonth,
                                  @NotNull @Positive Integer expYear,
                                  @NotBlank String cvc) {
}
