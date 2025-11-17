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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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


        assertThat(breakdown.baseAmount()).isEqualByComparingTo("100.00");
        assertThat(breakdown.processingFee()).isEqualByComparingTo("4.00");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("1.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("5.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("95.00");

        verify(fxRateProvider).getQuote("USD", "MXN");
    }

    @Test
    void calculateForPaymentLink_shouldWorkWithOnlyPercentageFee() {
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("200.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(new BigDecimal("0.025"));
        config.setFixedFee(null);
        config.setFxMarkupPct(null);

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", false);

        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId, recipientId, amount, currency
        );

        assertThat(breakdown.processingFee()).isEqualByComparingTo("5.00");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("5.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("195.00");
    }

    @Test
    void calculateForPaymentLink_shouldWorkWithOnlyFixedFee() {
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(BigDecimal.ZERO);     // 0%
        config.setFixedFee(new BigDecimal("2.50"));
        config.setFxMarkupPct(BigDecimal.ZERO);

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", false);

        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId, recipientId, amount, currency
        );

        assertThat(breakdown.processingFee()).isEqualByComparingTo("2.50");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("2.50");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("47.50");
    }

    @Test
    void calculateForPaymentLink_shouldReturnZeroFeesWhenAllConfigNullOrZero() {
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("80.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(null);
        config.setFixedFee(null);
        config.setFxMarkupPct(null);

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", false);

        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId, recipientId, amount, currency
        );

        assertThat(breakdown.processingFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("0.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void calculateForPaymentLink_shouldNotCallFxProviderWhenCurrencyEqualsPayout() {
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "MXN";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFixedFee(new BigDecimal("1.00"));
        config.setFxMarkupPct(new BigDecimal("0.01"));

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(config));

        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "fxEnabled", true);
        org.springframework.test.util.ReflectionTestUtils
                .setField(feeEngine, "payoutCurrency", "MXN");

        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId, recipientId, amount, currency
        );

        assertThat(breakdown.totalFees()).isEqualByComparingTo("5.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("95.00");

        verify(fxRateProvider, never()).getQuote(anyString(), anyString());
    }

    @Test
    void calculateForPaymentLink_shouldThrowWhenMerchantFeeConfigMissing() {
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                feeEngine.calculateForPaymentLink(merchantId, recipientId, amount, currency)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Merchant fee config not found for merchant " + merchantId);
    }
}
