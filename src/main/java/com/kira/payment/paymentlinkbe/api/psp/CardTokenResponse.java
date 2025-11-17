package com.kira.payment.paymentlinkbe.api.psp;

import com.kira.payment.paymentlinkbe.domain.psp.CardToken;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;

import java.time.Instant;

public record CardTokenResponse(String pspCode,
                                String pspToken,
                                String last4,
                                String brand,
                                Instant createdAt) {
    public static CardTokenResponse from(PspCode code, CardToken token) {
        return new CardTokenResponse(
                code.name(),
                token.token(),
                token.last4(),
                token.brand(),
                token.createdAt()
        );
    }
}
