package com.kira.payment.paymentlinkbe.api.psp;

import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkApplicationService;
import com.kira.payment.paymentlinkbe.domain.psp.CardTokenResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutTokenizationController {

    private final PaymentLinkApplicationService paymentLinkService;

    @PostMapping("/{slug}/tokenize")
    public ResponseEntity<CardTokenResponse> tokenizeForCheckout(
            @PathVariable String slug,
            @Valid @RequestBody TokenizeCardRequest request
    ) {
        CardTokenResult result = paymentLinkService.tokenizeForCheckout(slug, request);

        return ResponseEntity.ok(
                CardTokenResponse.from(result.pspCode(), result.token())
        );
    }
}
