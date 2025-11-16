package com.kira.payment.paymentlinkbe.application.paymentlink;

import com.kira.payment.paymentlinkbe.application.psp.PspOrchestratorService;
import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fee.FeeEngine;
import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.RecipientNotFoundException;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.domain.psp.ChargeStatus;
import com.kira.payment.paymentlinkbe.domain.psp.PspChargeResult;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.domain.psp.RoutedPspChargeResult;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Recipient;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.Psp;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.PspRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentLinkApplicationService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final MerchantRepository merchantRepository;
    private final RecipientRepository recipientRepository;
    private final PaymentRepository paymentRepository;
    private final PspOrchestratorService pspOrchestratorService;
    private final FeeEngine feeEngine;
    private final PspRepository pspRepository;

    @Value("${payment-link.public-base-url}")
    private String publicBaseUrl;

    @Transactional
    public PaymentLinkView createPaymentLink(CreatePaymentLinkCommand command) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime expiresAt = command.expiresAt() != null
                ? command.expiresAt()
                : now.plusDays(7);

        if (expiresAt.isBefore(now)) {
            throw new PaymentLinkInvalidStateException("Expiration date must be in the future");
        }

        Merchant merchant = merchantRepository.findById(command.merchantId())
                .orElseThrow(() -> new MerchantNotFoundException(command.merchantId()));

        Recipient recipient = null;
        if (command.recipientId() != null) {
            recipient = recipientRepository.findById(command.recipientId())
                    .orElseThrow(() -> new RecipientNotFoundException(command.recipientId()));
        }

        PaymentLink paymentLink = PaymentLink.builder()
                .publicId(UUID.randomUUID().toString())
                .slug(generateUniqueSlug())
                .merchant(merchant)
                .recipient(recipient)
                .amount(command.amount())
                .currency(command.currency())
                .description(command.description())
                .status(PaymentLinkStatus.CREATED)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        PaymentLink saved = paymentLinkRepository.save(paymentLink);

        FeeBreakdown feeBreakdown = feeEngine.calculateForPaymentLink(
                merchant.getId(),
                recipient != null ? recipient.getId() : null,
                command.amount(),
                command.currency()
        );
        String checkoutUrl = buildCheckoutUrl(saved.getSlug());

        return PaymentLinkView.from(saved, feeBreakdown, checkoutUrl);
    }

    private String generateUniqueSlug() {
        String candidate;
        do {
            candidate = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 10);
        } while (paymentLinkRepository.existsBySlug(candidate));
        return candidate;
    }

    private String buildCheckoutUrl(String slug) {
        return publicBaseUrl + "/" + slug;
    }

    @Transactional(readOnly = true)
    public PaymentLinkView getPaymentLink(String slug) {
        PaymentLink paymentLink = paymentLinkRepository.findBySlug(slug)
                .orElseThrow(() -> new PaymentLinkNotFoundException(slug));
        LocalDateTime now = LocalDateTime.now();
        if (paymentLink.getExpiresAt() != null
                && paymentLink.getExpiresAt().isBefore(now)
                && paymentLink.getStatus() != PaymentLinkStatus.EXPIRED) {
            paymentLink.setStatus(PaymentLinkStatus.EXPIRED);
        }

        FeeBreakdown feeBreakdown = feeEngine.calculateForPaymentLink(
                paymentLink.getMerchant().getId(),
                paymentLink.getRecipient() != null ? paymentLink.getRecipient().getId() : null,
                paymentLink.getAmount(),
                paymentLink.getCurrency()
        );

        String checkoutUrl = buildCheckoutUrl(paymentLink.getSlug());

        return PaymentLinkView.from(paymentLink, feeBreakdown, checkoutUrl);
    }

    @Transactional
    public ProcessPaymentResult processPayment(String slug, ProcessPaymentCommand command) {
        PaymentLink paymentLink = paymentLinkRepository.findBySlug(slug)
                .orElseThrow(() -> new PaymentLinkNotFoundException(slug));

        if (paymentLink.getStatus() == PaymentLinkStatus.PAID
                || paymentLink.getStatus() == PaymentLinkStatus.EXPIRED) {
            throw new PaymentLinkInvalidStateException(
                    "Payment link is not payable in status: " + paymentLink.getStatus()
            );
        }

        FeeBreakdown feeBreakdown = feeEngine.calculateForPaymentLink(
                paymentLink.getMerchant().getId(),
                paymentLink.getRecipient() != null ? paymentLink.getRecipient().getId() : null,
                paymentLink.getAmount(),
                paymentLink.getCurrency()
        );

        RoutedPspChargeResult routed = pspOrchestratorService.processPayment(
                command.pspToken(),
                paymentLink.getAmount(),
                paymentLink.getCurrency(),
                command.pspHint()
        );

        PspCode usedPspCode = routed.pspCode();
        PspChargeResult pspResult = routed.result();

        Psp pspEntity = pspRepository.findByCode(usedPspCode)
                .orElse(null);

        PaymentStatus paymentStatus = (pspResult.status() == ChargeStatus.SUCCEEDED)
                ? PaymentStatus.CAPTURED
                : PaymentStatus.FAILED;

        Payment payment = Payment.builder()
                .paymentLink(paymentLink)
                .merchant(paymentLink.getMerchant())
                .recipient(paymentLink.getRecipient())
                .psp(pspEntity)
                .pspReference(pspResult.pspChargeId())
                .status(paymentStatus)
                .amount(paymentLink.getAmount())
                .feeTotal(feeBreakdown.totalFees())
                .netAmount(feeBreakdown.finalAmount())
                .currency(paymentLink.getCurrency())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        if (paymentStatus == PaymentStatus.CAPTURED) {
            paymentLink.setStatus(PaymentLinkStatus.PAID);
            paymentLink.setUpdatedAt(LocalDateTime.now());
            paymentLinkRepository.save(paymentLink);
        }

        return ProcessPaymentResult.from(savedPayment, feeBreakdown, usedPspCode.name());
    }
}
