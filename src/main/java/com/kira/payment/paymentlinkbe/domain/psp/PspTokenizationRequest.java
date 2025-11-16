package com.kira.payment.paymentlinkbe.domain.psp;

public record PspTokenizationRequest(String cardNumber,
                                     Integer expMonth,
                                     Integer expYear,
                                     String cvv) {
}
