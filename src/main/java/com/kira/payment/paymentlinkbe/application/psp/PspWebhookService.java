package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PspWebhookService {
    private final PaymentRepository paymentRepository;
    private final PaymentLinkRepository paymentLinkRepository;

    @Transactional
    public void handleStripeWebhook(String pspReference, String status) {
        Payment payment = paymentRepository.findByPspReference(pspReference)
                .orElse(null);

        if (payment == null) {
            log.warn("Received Stripe webhook for unknown payment. pspReference={}, status={}",
                    pspReference, status);
            return;
        }

        PaymentStatus newStatus = mapStripeStatus(status);
        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());

        PaymentLink paymentLink = payment.getPaymentLink();
        if (newStatus == PaymentStatus.CAPTURED) {
            paymentLink.setStatus(PaymentLinkStatus.PAID);
            paymentLink.setUpdatedAt(LocalDateTime.now());
            paymentLinkRepository.save(paymentLink);
        }

        paymentRepository.save(payment);
    }

    @Transactional
    public void handleAdyenWebhook(String pspReference, String eventCode, boolean success) {
        Payment payment = paymentRepository.findByPspReference(pspReference)
                .orElse(null);

        if (payment == null) {
            log.warn("Received Adyen webhook for unknown payment. pspReference={}, eventCode={}, success={}",
                    pspReference, eventCode, success);
            return;
        }

        PaymentStatus newStatus = mapAdyenStatus(eventCode, success);
        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());

        PaymentLink paymentLink = payment.getPaymentLink();
        if (newStatus == PaymentStatus.CAPTURED) {
            paymentLink.setStatus(PaymentLinkStatus.PAID);
            paymentLink.setUpdatedAt(LocalDateTime.now());
            paymentLinkRepository.save(paymentLink);
        }

        paymentRepository.save(payment);
    }

    private PaymentStatus mapStripeStatus(String status) {
        if ("CAPTURED".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status)) {
            return PaymentStatus.CAPTURED;
        }
        return PaymentStatus.FAILED;
    }

    private PaymentStatus mapAdyenStatus(String eventCode, boolean success) {
        if ("AUTHORISATION".equalsIgnoreCase(eventCode) && success) {
            return PaymentStatus.CAPTURED;
        }
        return PaymentStatus.FAILED;
    }
}
