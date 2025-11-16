package com.kira.payment.paymentlinkbe.api.psp;

import com.kira.payment.paymentlinkbe.application.webhook.WebhookApplicationService;
import com.kira.payment.paymentlinkbe.domain.psp.ChargeStatus;
import com.kira.payment.paymentlinkbe.domain.psp.PspChargeWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/psp")
@RequiredArgsConstructor
public class PspWebhookController {

    private final WebhookApplicationService webhookApplicationService;

    @PostMapping("/charges")
    public ResponseEntity<Void> handleChargeWebhook(
            @RequestBody PspChargeWebhookRequest request
    ) {
        if (request.status() == ChargeStatus.SUCCEEDED) {
            webhookApplicationService.handlePspChargeSucceeded(
                    request.pspCode(),
                    request.pspChargeId(),
                    request.paymentId()
            );
        } else if (request.status() == ChargeStatus.FAILED) {
            webhookApplicationService.handlePspChargeFailed(
                    request.pspCode(),
                    request.pspChargeId(),
                    request.paymentId(),
                    request.failureCode(),
                    request.failureMessage()
            );
        }
        return ResponseEntity.ok().build();
    }
}
