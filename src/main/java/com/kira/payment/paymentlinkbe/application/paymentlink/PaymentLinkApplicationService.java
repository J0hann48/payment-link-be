package com.kira.payment.paymentlinkbe.application.paymentlink;

import com.kira.payment.paymentlinkbe.api.payment.UpdatePaymentLinkCommand;
import com.kira.payment.paymentlinkbe.api.psp.TokenizeCardRequest;
import com.kira.payment.paymentlinkbe.application.psp.PspOrchestratorService;
import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fee.FeeEngine;
import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.merchant.RecipientNotFoundException;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentFeeType;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.domain.psp.*;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Recipient;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentFee;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.Psp;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.PspRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final Map<String, PspClient> pspClients;

    @Value("${payment-link.public-base-url}")
    private String publicBaseUrl;
    @Value("${payment-link.default-psp}")
    private String defaultPspCode;

    @Transactional
    public PaymentLinkView createPaymentLink(CreatePaymentLinkCommand command) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime expiresAt = command.expiresAt() != null
                ? command.expiresAt().atStartOfDay()
                : now.plusDays(10);

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
        PspCode preferredPsp = resolvePreferredPsp(merchant);
        return PaymentLinkView.from(saved, feeBreakdown, checkoutUrl, preferredPsp);
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

        PspCode preferredPsp = resolvePreferredPsp(paymentLink.getMerchant());

        return PaymentLinkView.from(paymentLink, feeBreakdown, checkoutUrl, preferredPsp);
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

        PspCode preferredPsp = resolvePreferredPsp(paymentLink.getMerchant());
        String pspHint = preferredPsp.name();

        RoutedPspChargeResult routed = pspOrchestratorService.processPayment(
                command.pspToken(),
                paymentLink.getAmount(),
                paymentLink.getCurrency(),
                pspHint
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

        if (paymentStatus == PaymentStatus.CAPTURED) {

            // PROCESSING (fixed + porcentaje)
            if (feeBreakdown.processingFee().compareTo(BigDecimal.ZERO) > 0) {
                payment.getFees().add(PaymentFee.builder()
                        .payment(payment)
                        .type(PaymentFeeType.PROCESSING)
                        .amount(feeBreakdown.processingFee())
                        .currency(paymentLink.getCurrency())
                        .build());
            }

            // FX
            if (feeBreakdown.fxFee().compareTo(BigDecimal.ZERO) > 0) {
                payment.getFees().add(PaymentFee.builder()
                        .payment(payment)
                        .type(PaymentFeeType.FX)
                        .amount(feeBreakdown.fxFee())
                        .currency(paymentLink.getCurrency())
                        .build());
            }

            if (feeBreakdown.incentiveDiscount().compareTo(BigDecimal.ZERO) > 0) {
                payment.getFees().add(PaymentFee.builder()
                        .payment(payment)
                        .type(PaymentFeeType.INCENTIVE_DISCOUNT)
                        .amount(feeBreakdown.incentiveDiscount().negate())
                        .currency(paymentLink.getCurrency())
                        .build());
            }
        }

        Payment savedPayment = paymentRepository.save(payment);

        if (paymentStatus == PaymentStatus.CAPTURED) {
            paymentLink.setStatus(PaymentLinkStatus.PAID);
            paymentLink.setUpdatedAt(LocalDateTime.now());
            paymentLinkRepository.save(paymentLink);
        }

        return ProcessPaymentResult.from(savedPayment, feeBreakdown, usedPspCode.name());
    }
    private PspCode resolvePreferredPsp(Merchant merchant) {
        return PspCode.valueOf(defaultPspCode.toUpperCase());
    }

    @Transactional
    public CardTokenResult tokenizeForCheckout(String slug, TokenizeCardRequest request) {
        PaymentLink paymentLink = paymentLinkRepository.findBySlug(slug)
                .orElseThrow(() -> new PaymentLinkNotFoundException(slug));
        LocalDateTime now = LocalDateTime.now();
        if (paymentLink.getExpiresAt() != null
                && paymentLink.getExpiresAt().isBefore(now)) {
            paymentLink.setStatus(PaymentLinkStatus.EXPIRED);
        }
        if (paymentLink.getStatus() == PaymentLinkStatus.PAID
                || paymentLink.getStatus() == PaymentLinkStatus.EXPIRED) {
            throw new PaymentLinkInvalidStateException(
                    "Payment link is not payable in status: " + paymentLink.getStatus()
            );
        }
        PspCode preferredPsp = resolvePreferredPsp(paymentLink.getMerchant());
        PspClient pspClient = findClientByCode(preferredPsp);

        CardToken token = pspClient.tokenizeCard(
                new PspTokenizationRequest(
                        request.cardNumber(),
                        request.expMonth(),
                        request.expYear(),
                        request.cvc()
                )
        );

        return new CardTokenResult(preferredPsp, token);
    }

    private PspClient findClientByCode(PspCode code) {
        return pspClients.values().stream()
                .filter(c -> c.getCode() == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("PSP client not found for code: " + code));
    }

    @Transactional(readOnly = true)
    public List<PaymentLinkView> listByMerchant(Long merchantId) {

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));

        List<PaymentLink> links =
                paymentLinkRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.getId());

        LocalDateTime now = LocalDateTime.now();

        return links.stream()
                .map(link -> {
                    if (link.getExpiresAt() != null
                            && link.getExpiresAt().isBefore(now)
                            && link.getStatus() != PaymentLinkStatus.EXPIRED) {
                        link.setStatus(PaymentLinkStatus.EXPIRED);
                    }

                    FeeBreakdown feeBreakdown = feeEngine.calculateForPaymentLink(
                            link.getMerchant().getId(),
                            link.getRecipient() != null ? link.getRecipient().getId() : null,
                            link.getAmount(),
                            link.getCurrency()
                    );

                    String checkoutUrl = buildCheckoutUrl(link.getSlug());
                    PspCode preferredPsp = resolvePreferredPsp(link.getMerchant());

                    return PaymentLinkView.from(link, feeBreakdown, checkoutUrl, preferredPsp);
                })
                .toList();
    }

    @Transactional
    public PaymentLinkView updatePaymentLink(String slug, UpdatePaymentLinkCommand command) {
        PaymentLink paymentLink = paymentLinkRepository.findBySlug(slug)
                .orElseThrow(() -> new PaymentLinkNotFoundException(slug));

        if (command.merchantId() != null &&
                !paymentLink.getMerchant().getId().equals(command.merchantId())) {
            throw new PaymentLinkInvalidStateException(
                    "Payment link does not belong to merchant " + command.merchantId()
            );
        }

        if (paymentLink.getStatus() == PaymentLinkStatus.PAID
                || paymentLink.getStatus() == PaymentLinkStatus.EXPIRED) {
            throw new PaymentLinkInvalidStateException(
                    "Payment link is not editable in status: " + paymentLink.getStatus()
            );
        }

        if (command.recipientId() != null) {
            Recipient recipient = recipientRepository.findById(command.recipientId())
                    .orElseThrow(() -> new RecipientNotFoundException(command.recipientId()));
            paymentLink.setRecipient(recipient);
        }

        paymentLink.setAmount(command.amount());
        paymentLink.setCurrency(command.currency());
        paymentLink.setDescription(command.description());

        if (command.expiresAt() != null) {
            paymentLink.setExpiresAt(command.expiresAt().atStartOfDay());
        }

        paymentLink.setUpdatedAt(LocalDateTime.now());

        PaymentLink saved = paymentLinkRepository.save(paymentLink);

        FeeBreakdown feeBreakdown = feeEngine.calculateForPaymentLink(
                saved.getMerchant().getId(),
                saved.getRecipient() != null ? saved.getRecipient().getId() : null,
                saved.getAmount(),
                saved.getCurrency()
        );
        String checkoutUrl = buildCheckoutUrl(saved.getSlug());
        PspCode preferredPsp = resolvePreferredPsp(saved.getMerchant());

        return PaymentLinkView.from(saved, feeBreakdown, checkoutUrl, preferredPsp);
    }

    @Transactional
    public void deletePaymentLink(String slug, Long merchantId) {
        PaymentLink paymentLink = paymentLinkRepository
                .findBySlugAndMerchantId(slug, merchantId)
                .orElseThrow(() -> new PaymentLinkNotFoundException(slug));

        if (paymentLink.getStatus() == PaymentLinkStatus.PAID) {
            throw new PaymentLinkInvalidStateException(
                    "Cannot delete a PAID payment link"
            );
        }
        paymentLink.setStatus(PaymentLinkStatus.EXPIRED);
        paymentLink.setUpdatedAt(LocalDateTime.now());
        paymentLinkRepository.save(paymentLink);
    }
}
