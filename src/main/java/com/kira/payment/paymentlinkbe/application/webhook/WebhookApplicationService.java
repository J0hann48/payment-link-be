package com.kira.payment.paymentlinkbe.application.webhook;

import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.WebhookEvent;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.WebhookEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookApplicationService {
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public void handlePspChargeSucceeded(PspCode pspCode, String pspChargeId, String paymentId) {
        log.info(
                "[Webhook] PSP charge SUCCEEDED: pspCode={}, pspChargeId={}, paymentId={}",
                pspCode, pspChargeId, paymentId
        );
        saveEvent(pspCode, "CHARGE_SUCCEEDED", pspChargeId, paymentId, null, null);

        paymentRepository.findByPspReference(pspChargeId)
                .ifPresent(payment -> {
                    PaymentStatus current = payment.getStatus();

                    switch (current) {
                        case CAPTURED -> {
                            // Idempotente: ya está capturado
                            log.info(
                                    "[Webhook] Ignoring SUCCEEDED for already CAPTURED payment id={} pspRef={}",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case FAILED -> {
                            // Conflicto: antes lo marcaste FAILED
                            log.warn(
                                    "[Webhook] Received SUCCEEDED for FAILED payment id={} pspRef={}. Keeping FAILED.",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case REFUNDED -> {
                            // Conflicto: ya fue reembolsado
                            log.warn(
                                    "[Webhook] Received SUCCEEDED for REFUNDED payment id={} pspRef={}. Keeping REFUNDED.",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case PENDING, AUTHORIZED -> {
                            // Transición normal a CAPTURED
                            log.info(
                                    "[Webhook] Marking payment id={} as CAPTURED from status={} via SUCCEEDED webhook",
                                    payment.getId(), current
                            );
                            payment.setStatus(PaymentStatus.CAPTURED);
                            payment.setUpdatedAt(LocalDateTime.now());
                            paymentRepository.save(payment);
                        }
                    }
                });
    }

    @Transactional
    public void handlePspChargeFailed(
            PspCode pspCode,
            String pspChargeId,
            String paymentId,
            String failureCode,
            String failureMessage
    ) {
        log.info(
                "[Webhook] PSP charge FAILED: pspCode={}, pspChargeId={}, paymentId={}, failureCode={}, failureMessage={}",
                pspCode, pspChargeId, paymentId, failureCode, failureMessage
        );

        saveEvent(pspCode, "CHARGE_FAILED", pspChargeId, paymentId, failureCode, failureMessage);

        paymentRepository.findByPspReference(pspChargeId)
                .ifPresent(payment -> {
                    PaymentStatus current = payment.getStatus();

                    switch (current) {
                        case FAILED -> {
                            // Idempotente
                            log.info(
                                    "[Webhook] Ignoring FAILED for already FAILED payment id={} pspRef={}",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case CAPTURED -> {
                            // Conflicto: ya se capturó el cobro, no lo tiramos a FAILED
                            log.warn(
                                    "[Webhook] Received FAILED for CAPTURED payment id={} pspRef={}. Keeping CAPTURED.",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case REFUNDED -> {
                            // Conflicto: ya fue reembolsado
                            log.warn(
                                    "[Webhook] Received FAILED for REFUNDED payment id={} pspRef={}. Keeping REFUNDED.",
                                    payment.getId(), pspChargeId
                            );
                        }
                        case PENDING, AUTHORIZED -> {
                            // Transición normal a FAILED
                            log.info(
                                    "[Webhook] Marking payment id={} as FAILED from status={} via FAILED webhook",
                                    payment.getId(), current
                            );
                            payment.setStatus(PaymentStatus.FAILED);
                            payment.setUpdatedAt(LocalDateTime.now());
                            paymentRepository.save(payment);
                        }
                    }
                });
    }

    private void saveEvent(
            PspCode pspCode,
            String eventType,
            String pspChargeId,
            String paymentId,
            String failureCode,
            String failureMessage
    ) {
        String payloadJson = """
                {
                  "pspChargeId":"%s",
                  "paymentId":"%s",
                  "failureCode":"%s",
                  "failureMessage":"%s"
                }
                """.formatted(
                pspChargeId,
                paymentId,
                failureCode != null ? failureCode : "",
                failureMessage != null ? failureMessage : ""
        );

        WebhookEvent event = WebhookEvent.builder()
                .pspName(pspCode.name())
                .eventType(eventType)
                .payload(payloadJson)
                .createdAt(LocalDateTime.now())
                .build();

        webhookEventRepository.save(event);
    }
}
