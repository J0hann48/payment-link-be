package com.kira.payment.paymentlinkbe.application.fee;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fx.FxQuote;
import com.kira.payment.paymentlinkbe.domain.fx.FxRateProvider;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultFeeEngineTest {
    @Mock
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    @Mock
    private FxRateProvider fxRateProvider;

    @InjectMocks
    private DefaultFeeEngine feeEngine;

    @Test
    void calculateForPaymentLink_shouldUseMerchantFeeConfig_whenFxDisabled() {
        // given
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFixedFee(new BigDecimal("1.00"));
        config.setFxMarkupPct(new BigDecimal("0.01"));

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", false);

        // when
        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId,
                recipientId,
                amount,
                currency
        );

        // then
        assertThat(breakdown.baseAmount()).isEqualByComparingTo("100.00");
        assertThat(breakdown.processingFee()).isEqualByComparingTo("4.00");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("1.00");
        assertThat(breakdown.incentiveDiscount()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("5.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("95.00");
        assertThat(breakdown.currency()).isEqualTo("USD");

        verify(fxRateProvider, never()).getQuote(anyString(), anyString());
    }

    @Test
    void calculateForPaymentLink_shouldCallFxProvider_whenFxEnabledAndDifferentCurrency() {
        // given
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFixedFee(new BigDecimal("1.00"));
        config.setFxMarkupPct(new BigDecimal("0.01"));

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        // fxEnabled = true, payoutCurrency = "MXN"
        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", true);
        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "payoutCurrency", "MXN");
        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "markupPercent", new BigDecimal("0.02"));

        FxQuote fxQuote = new FxQuote(
                "USD",
                "MXN",
                new BigDecimal("17.50"),
                BigDecimal.ZERO,
                new BigDecimal("17.50"),
                java.time.Instant.now()
        );
        when(fxRateProvider.getQuote("USD", "MXN"))
                .thenReturn(fxQuote);

        // when
        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId,
                recipientId,
                amount,
                currency
        );

        // then (fees igual que en el test anterior)
        assertThat(breakdown.totalFees()).isEqualByComparingTo("5.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("95.00");
        verify(fxRateProvider).getQuote("USD", "MXN");
    }
}
