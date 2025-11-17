package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkView;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentLinkResponse(
        Long id,
        String publicId,
        String slug,
        Long merchantId,
        Long recipientId,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        String checkoutUrl,
        FeeBreakdownResponse feeBreakdown,
        String preferredPsp
) {

    public static PaymentLinkResponse from(PaymentLinkView view) {
        FeePreviewResponse feePreview = view.feeBreakdown() != null
                ? FeePreviewResponse.from(view.feeBreakdown())
                : null;

        return new PaymentLinkResponse(
                view.id(),
                view.publicId(),
                view.slug(),
                view.merchantId(),
                view.recipientId(),
                view.amount(),
                view.currency(),
                view.description(),
                view.status().name(),
                view.expiresAt(),
                view.createdAt(),
                view.checkoutUrl(),
                FeeBreakdownResponse.from(view.feeBreakdown()),
                view.preferredPsp() != null ? view.preferredPsp() : null
        );
    }

    public record FeePreviewResponse(
            BigDecimal baseAmount,
            BigDecimal processingFee,
            BigDecimal fxFee,
            BigDecimal incentiveDiscount,
            BigDecimal totalFees,
            BigDecimal finalAmount,
            String currency
    ) {
        public static FeePreviewResponse from(FeeBreakdown breakdown) {
            return new FeePreviewResponse(
                    breakdown.baseAmount(),
                    breakdown.processingFee(),
                    breakdown.fxFee(),
                    breakdown.incentiveDiscount(),
                    breakdown.totalFees(),
                    breakdown.finalAmount(),
                    breakdown.currency()
            );
        }
    }
}
