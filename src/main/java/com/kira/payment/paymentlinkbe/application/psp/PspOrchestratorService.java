package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.psp.*;
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

    public RoutedPspChargeResult processPayment(
            String pspToken,
            BigDecimal amount,
            String currency,
            String pspHint
    ) {
        PspClient stripe = findClientByCode(PspCode.STRIPE);
        PspClient adyen  = findClientByCode(PspCode.ADYEN);

        PspClient primary   = stripe;
        PspClient secondary = adyen;

        if (pspHint != null && PspCode.ADYEN.name().equalsIgnoreCase(pspHint)) {
            primary   = adyen;
            secondary = stripe;
        }

        PspChargeRequest request = new PspChargeRequest(
                pspToken,
                amount,
                currency
        );
        try {
            log.info("Trying primary PSP={} for amount={} {}",
                    primary.getCode(), amount, currency);

            PspChargeResult primaryResult = primary.charge(request);

            if (primaryResult.status() == ChargeStatus.SUCCEEDED) {
                return new RoutedPspChargeResult(primary.getCode(), primaryResult);
            }

            log.warn(
                    "Primary PSP={} returned FAILED: failureCode={}, failureMessage={}",
                    primary.getCode(),
                    primaryResult.failureCode(),
                    primaryResult.failureMessage()
            );
        } catch (Exception e) {
            log.warn("Primary PSP={} threw exception: {}",
                    primary.getCode(), e.getMessage(), e);
        }

        try {
            log.info("Trying secondary PSP={} for amount={} {}",
                    secondary.getCode(), amount, currency);

            PspChargeResult secondaryResult = secondary.charge(request);

            if (secondaryResult.status() == ChargeStatus.SUCCEEDED) {
                return new RoutedPspChargeResult(secondary.getCode(), secondaryResult);
            }

            log.error(
                    "Secondary PSP={} also FAILED: failureCode={}, failureMessage={}",
                    secondary.getCode(),
                    secondaryResult.failureCode(),
                    secondaryResult.failureMessage()
            );
            throw new PspRoutingException(
                    "Both PSPs returned FAILED: primary=%s, secondary=%s"
                            .formatted(primary.getCode(), secondary.getCode())
            );
        } catch (Exception e) {
            log.error("Secondary PSP={} also threw exception: {}",
                    secondary.getCode(), e.getMessage(), e);
            throw new PspRoutingException("Both PSPs failed with exception", e);
        }
    }

    private PspClient findClientByCode(PspCode code) {
        return pspClients.values().stream()
                .filter(c -> c.getCode() == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("PSP client not found for code: " + code));
    }
}
