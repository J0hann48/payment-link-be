package com.kira.payment.paymentlinkbe.infraestructure.psp.adyen;

import com.kira.payment.paymentlinkbe.api.error.CardTokenizationException;
import com.kira.payment.paymentlinkbe.domain.psp.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdyenPspClientMock implements PspClient {

    private final PspWebhookPublisher webhookPublisher;
    private final Map<String, CardToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, ChargeStatus> charges = new ConcurrentHashMap<>();

    @Override
    public PspCode getCode() {
        return PspCode.ADYEN;
    }

    @Override
    public CardToken tokenizeCard(PspTokenizationRequest request) {
        if (request.cardNumber() == null || request.cardNumber().length() < 16) {
            log.error("Card number is empty");
            throw new CardTokenizationException(
                    "INVALID_CARD_NUMBER",
                    "Card number must be at least 16 digits"
            );
        }

        String token = "ady_tok_" + UUID.randomUUID();
        String last4 = request.cardNumber()
                .substring(request.cardNumber().length() - 4);
        String brand = inferBrand(request.cardNumber());

        CardToken cardToken = new CardToken(
                token,
                last4,
                brand,
                Instant.now()
        );

        tokens.put(token, cardToken);
        return cardToken;
    }

    @Override
    public PspChargeResult charge(PspChargeRequest request) {
        if ("sim_adyen_exception".equalsIgnoreCase(request.cardToken())) {
            throw new RuntimeException("Simulated Adyen outage");
        }

        if ("sim_adyen_failed".equalsIgnoreCase(request.cardToken())) {
            return PspChargeResult.failure(
                    "ch_simulated_adyen",
                    "SIM_ADYEN_FAILED",
                    "Simulated Adyen failure"
            );
        }
        if (!tokens.containsKey(request.cardToken())) {
            String pspChargeId = "ady_ch_" + UUID.randomUUID();
            charges.put(pspChargeId, ChargeStatus.FAILED);

            String failureCode = "INVALID_TOKEN";
            String failureMessage = "Card token not found in Adyen mock";

            webhookPublisher.publishChargeFailed(
                    PspCode.ADYEN,
                    pspChargeId,
                    "pay_mock_" + UUID.randomUUID(),
                    failureCode,
                    failureMessage
            );

            return PspChargeResult.failure(pspChargeId, failureCode, failureMessage);
        }

        String pspChargeId = "ady_ch_" + UUID.randomUUID();
        charges.put(pspChargeId, ChargeStatus.SUCCEEDED);

        webhookPublisher.publishChargeSucceeded(
                PspCode.ADYEN,
                pspChargeId,
                "pay_mock_" + UUID.randomUUID()
        );

        return PspChargeResult.success(
                pspChargeId,
                request.amount(),
                request.currency()
        );
    }

    private String inferBrand(String cardNumber) {
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.startsWith("5")) return "MASTERCARD";
        return "UNKNOWN";
    }
}
