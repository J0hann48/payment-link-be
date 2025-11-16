package com.kira.payment.paymentlinkbe.application.fee;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fee.FeeEngine;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class DefaultFeeEngine implements FeeEngine {

    private final MerchantFeeConfigRepository merchantFeeConfigRepository;
    // TODO: más adelante podrías inyectar aquí IncentiveRuleRepository, FxProvider, etc.

    @Transactional
    @Override
    public FeeBreakdown calculateForPaymentLink(Long merchantId, Long recipientId, BigDecimal amount, String currency) {
        MerchantFeeConfig config = merchantFeeConfigRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant fee config not found for merchant " + merchantId));

        // Normalizar monto
        BigDecimal baseAmount = amount.setScale(2, RoundingMode.HALF_UP);

        // 1) Processing fee = amount * percentageFee + fixedFee
        BigDecimal processingFee = calculatePercentageFee(baseAmount, config.getPercentageFee())
                .add(nullSafe(config.getFixedFee()));

        // 2) FX fee = amount * fxMarkupPct  (simplificado para el case study)
        BigDecimal fxFee = calculatePercentageFee(baseAmount, config.getFxMarkupPct());

        // 3) Incentive discount (por ahora cero, luego lo conectas a IncentiveRule)
        BigDecimal incentiveDiscount = BigDecimal.ZERO;
        // TODO: aplicar incentivos usando recipientId / reglas de merchant

        // 4) Total fees
        BigDecimal totalFees = processingFee
                .add(fxFee)
                .subtract(incentiveDiscount);

        // 5) Final amount (lo que recibe el merchant)
        BigDecimal finalAmount = baseAmount.subtract(totalFees);

        return new FeeBreakdown(
                baseAmount,
                processingFee.setScale(2, RoundingMode.HALF_UP),
                fxFee.setScale(2, RoundingMode.HALF_UP),
                incentiveDiscount.setScale(2, RoundingMode.HALF_UP),
                totalFees.setScale(2, RoundingMode.HALF_UP),
                finalAmount.setScale(2, RoundingMode.HALF_UP),
                currency
        );
    }

    private BigDecimal calculatePercentageFee(BigDecimal base, BigDecimal pct) {
        if (pct == null || BigDecimal.ZERO.compareTo(pct) == 0) {
            return BigDecimal.ZERO;
        }
        return base.multiply(pct);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
