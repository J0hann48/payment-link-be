package com.kira.payment.paymentlinkbe.application.fee;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fee.FeeEngine;
import com.kira.payment.paymentlinkbe.domain.fx.FxQuote;
import com.kira.payment.paymentlinkbe.domain.fx.FxRateProvider;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultFeeEngine implements FeeEngine {

    private final MerchantFeeConfigRepository merchantFeeConfigRepository;
    private final FxRateProvider fxRateProvider;

    @Value("${fx.enabled:false}")
    private boolean fxEnabled;
    @Value("${fx.payout-currency:MXN}")
    private String payoutCurrency;
    @Value("${fx.markup-percent:0.00}")
    private BigDecimal markupPercent;

    @Transactional
    @Override
    public FeeBreakdown calculateForPaymentLink(
            Long merchantId,
            Long recipientId,
            BigDecimal amount,
            String currency
    ) {
        MerchantFeeConfig config = merchantFeeConfigRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant fee config not found for merchant " + merchantId));
        BigDecimal baseAmount = amount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal processingFee = calculatePercentageFee(baseAmount, config.getPercentageFee())
                .add(nullSafe(config.getFixedFee()));

        BigDecimal fxFee = calculatePercentageFee(baseAmount, config.getFxMarkupPct());

        BigDecimal incentiveDiscount = BigDecimal.ZERO;

        BigDecimal totalFees = processingFee
                .add(fxFee)
                .subtract(incentiveDiscount);
        BigDecimal finalAmount = baseAmount.subtract(totalFees);
        if (fxEnabled && !currency.equalsIgnoreCase(payoutCurrency)) {
            FxQuote quote = fxRateProvider.getQuote(currency, payoutCurrency);

            BigDecimal effectiveRate = quote.effectiveRate();
            BigDecimal rateWithExtraMarkup = effectiveRate
                    .multiply(BigDecimal.ONE.add(nullSafe(markupPercent)))
                    .setScale(6, RoundingMode.HALF_UP);

            BigDecimal payoutAmount = finalAmount
                    .multiply(rateWithExtraMarkup)
                    .setScale(2, RoundingMode.HALF_UP);

            log.info(
                    "FX breakdown for merchantId={}: net={} {}, providerRate={} {}, rateWithMarkup={} {}, payout={} {}",
                    merchantId,
                    finalAmount, currency,
                    effectiveRate, payoutCurrency,
                    rateWithExtraMarkup, payoutCurrency,
                    payoutAmount, payoutCurrency
            );
        }

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
