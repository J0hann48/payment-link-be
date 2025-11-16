package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.psp.PspChargeResult;
import com.kira.payment.paymentlinkbe.domain.psp.PspClient;
import com.kira.payment.paymentlinkbe.domain.psp.PspRoutingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PspOrchestratorService {

    private final Map<String, PspClient> pspClients;

    public PspChargeResult processPayment(
            String pspToken,
            BigDecimal amount,
            String currency,
            String pspHint
    ) {
        PspClient stripe = findClientByCode("STRIPE");
        PspClient adyen  = findClientByCode("ADYEN");

        PspClient primary = stripe;
        PspClient secondary = adyen;

        if ("ADYEN".equalsIgnoreCase(pspHint)) {
            primary = adyen;
            secondary = stripe;
        }

        try {
            log.info("Trying primary PSP={} for amount={} {}", primary.code(), amount, currency);
            return primary.charge(pspToken, amount, currency);
        } catch (Exception e) {
            log.warn("Primary PSP={} failed: {}", primary.code(), e.getMessage());
        }

        try {
            log.info("Trying secondary PSP={} for amount={} {}", secondary.code(), amount, currency);
            return secondary.charge(pspToken, amount, currency);
        } catch (Exception e) {
            log.error("Secondary PSP={} also failed: {}", secondary.code(), e.getMessage());
            throw new PspRoutingException("Both PSPs failed", e);
        }
    }

    private PspClient findClientByCode(String code) {
        return pspClients.values().stream()
                .filter(c -> code.equalsIgnoreCase(c.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PSP client not found for code: " + code));
    }
}
