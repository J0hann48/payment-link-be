package com.kira.payment.paymentlinkbe.infraestructure.psp.adyen;

import com.kira.payment.paymentlinkbe.domain.psp.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        if (request.cardNumber() == null || request.cardNumber().length() < 12) {
            throw new IllegalArgumentException("Invalid card number for Adyen mock");
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
