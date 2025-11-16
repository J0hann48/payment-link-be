package com.kira.payment.paymentlinkbe.api.psp;

import com.kira.payment.paymentlinkbe.application.psp.PspWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/psp/webhook")
@RequiredArgsConstructor
public class PspWebhookController {

    private final PspWebhookService pspWebhookService;

    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.OK)
    public WebhookAckResponse handleStripeWebhook(
            @Valid @RequestBody StripeWebhookRequest request
    ) {
        pspWebhookService.handleStripeWebhook(
                request.pspReference(),
                request.status()
        );
        return new WebhookAckResponse(true);
    }

    @PostMapping("/adyen")
    @ResponseStatus(HttpStatus.OK)
    public WebhookAckResponse handleAdyenWebhook(
            @Valid @RequestBody AdyenWebhookRequest request
    ) {
        boolean success = "true".equalsIgnoreCase(request.success());

        pspWebhookService.handleAdyenWebhook(
                request.pspReference(),
                request.eventCode(),
                success
        );
        return new WebhookAckResponse(true);
    }
}
