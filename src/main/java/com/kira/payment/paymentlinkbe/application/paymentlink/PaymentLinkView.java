package com.kira.payment.paymentlinkbe.application.paymentlink;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Recipient;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentLinkView(
        Long id,
        String publicId,
        String slug,
        Long merchantId,
        Long recipientId,
        BigDecimal amount,
        String currency,
        String description,
        PaymentLinkStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        String checkoutUrl,
        FeeBreakdown feeBreakdown,
        String preferredPsp
) {

    public static PaymentLinkView from(
            PaymentLink paymentLink,
            FeeBreakdown feeBreakdown,
            String checkoutUrl,
            PspCode preferredPsp
    ) {
        Merchant merchant = paymentLink.getMerchant();
        Recipient recipient = paymentLink.getRecipient();

        return new PaymentLinkView(
                paymentLink.getId(),
                paymentLink.getPublicId(),
                paymentLink.getSlug(),
                merchant != null ? merchant.getId() : null,
                recipient != null ? recipient.getId() : null,
                paymentLink.getAmount(),
                paymentLink.getCurrency(),
                paymentLink.getDescription(),
                paymentLink.getStatus(),
                paymentLink.getExpiresAt(),
                paymentLink.getCreatedAt(),
                checkoutUrl,
                feeBreakdown,
                preferredPsp.name()
        );
    }
}
