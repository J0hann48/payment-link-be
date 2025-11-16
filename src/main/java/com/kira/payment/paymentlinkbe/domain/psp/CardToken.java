package com.kira.payment.paymentlinkbe.domain.psp;

import java.time.Instant;

public record CardToken(String token,
                        String last4,
                        String brand,
                        Instant createdAt) {
}
