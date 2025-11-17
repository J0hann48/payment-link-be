package com.kira.payment.paymentlinkbe.api.psp;

import com.kira.payment.paymentlinkbe.domain.psp.CardToken;
import com.kira.payment.paymentlinkbe.domain.psp.PspClient;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.domain.psp.PspTokenizationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/psp")
@RequiredArgsConstructor
public class PspTokenizationController {
    private final Map<String, PspClient> pspClients;

    @PostMapping("/adyen/tokenize")
    public ResponseEntity<CardTokenResponse> tokenizeAdyen(
            @Valid @RequestBody TokenizeCardRequest request
    ) {
        PspClient adyen = findClientByCode(PspCode.ADYEN);

        CardToken token = adyen.tokenizeCard(
                new PspTokenizationRequest(
                        request.cardNumber(),
                        request.expMonth(),
                        request.expYear(),
                        request.cvc()
                )
        );

        return ResponseEntity.ok(CardTokenResponse.from(PspCode.ADYEN, token));
    }

    @PostMapping("/stripe/tokenize")
    public ResponseEntity<CardTokenResponse> tokenizeStripe(
            @Valid @RequestBody TokenizeCardRequest request
    ) {
        PspClient stripe = findClientByCode(PspCode.STRIPE);

        CardToken token = stripe.tokenizeCard(
                new PspTokenizationRequest(
                        request.cardNumber(),
                        request.expMonth(),
                        request.expYear(),
                        request.cvc()
                )
        );

        return ResponseEntity.ok(CardTokenResponse.from(PspCode.STRIPE, token));
    }

    private PspClient findClientByCode(PspCode code) {
        return pspClients.values().stream()
                .filter(c -> c.getCode() == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("PSP client not found for code: " + code));
    }
}
