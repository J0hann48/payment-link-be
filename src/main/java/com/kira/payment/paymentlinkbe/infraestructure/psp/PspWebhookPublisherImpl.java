package com.kira.payment.paymentlinkbe.infraestructure.psp;

import com.kira.payment.paymentlinkbe.application.webhook.WebhookApplicationService;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.domain.psp.PspWebhookPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PspWebhookPublisherImpl implements PspWebhookPublisher {

    private final WebhookApplicationService webhookApplicationService;

    @Override
    public void publishChargeSucceeded(PspCode pspCode, String pspChargeId, String paymentLinkId) {
        webhookApplicationService.handlePspChargeSucceeded(pspCode, pspChargeId, paymentLinkId);
    }

    @Override
    public void publishChargeFailed(PspCode pspCode, String pspChargeId, String paymentLinkId, String failureCode, String failureMessage) {
        webhookApplicationService.handlePspChargeFailed(pspCode, pspChargeId, paymentLinkId, failureCode, failureMessage);
    }
}
