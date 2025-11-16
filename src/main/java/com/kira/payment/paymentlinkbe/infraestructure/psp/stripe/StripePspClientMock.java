package com.kira.payment.paymentlinkbe.infraestructure.psp.stripe;

import com.kira.payment.paymentlinkbe.domain.psp.PspChargeResult;
import com.kira.payment.paymentlinkbe.domain.psp.PspClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Component
public class StripePspClientMock implements PspClient {

    private final Random random = new Random();

    @Override
    public String code() {
        return "STRIPE";
    }

    @Override
    public PspChargeResult charge(String token, BigDecimal amount, String currency) {
        boolean success = random.nextDouble() > 0.2;
        if (!success) {
            throw new RuntimeException("Stripe simulated failure");
        }

        String pspRef = "STRIPE_" + token.substring(0, Math.min(8, token.length()));

        return new PspChargeResult(
                code(),
                pspRef,
                true,
                "CAPTURED",
                amount,
                currency
        );
    }
}
