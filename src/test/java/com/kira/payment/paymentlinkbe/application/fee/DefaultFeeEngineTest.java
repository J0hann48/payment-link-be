package com.kira.payment.paymentlinkbe.application.fee;

import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultFeeEngineTest {

    @Mock
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    @InjectMocks
    private DefaultFeeEngine feeEngine;

    @Test
    void calculateForPaymentLink_shouldCalculateFeesUsingMerchantConfig() {
        // given
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setPercentageFee(new BigDecimal("0.029"));
        config.setFixedFee(new BigDecimal("0.30"));
        config.setFxMarkupPct(new BigDecimal("0.011"));

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(java.util.Optional.of(config));

        // when
        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId,
                recipientId,
                amount,
                currency
        );

        // then
        assertThat(breakdown.baseAmount()).isEqualByComparingTo("100.00");
        assertThat(breakdown.processingFee()).isEqualByComparingTo("3.20");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("1.10");
        assertThat(breakdown.incentiveDiscount()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("4.30");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("95.70");
        assertThat(breakdown.currency()).isEqualTo("USD");
    }

    @Test
    void calculateForPaymentLink_shouldHandleNullConfigFieldsAsZero() {
        // given
        Long merchantId = 2L;
        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";

        MerchantFeeConfig config = new MerchantFeeConfig();

        when(merchantFeeConfigRepository.findByMerchantId(merchantId))
                .thenReturn(java.util.Optional.of(config));

        // when
        FeeBreakdown breakdown = feeEngine.calculateForPaymentLink(
                merchantId,
                null,
                amount,
                currency
        );

        // then
        assertThat(breakdown.processingFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.fxFee()).isEqualByComparingTo("0.00");
        assertThat(breakdown.totalFees()).isEqualByComparingTo("0.00");
        assertThat(breakdown.finalAmount()).isEqualByComparingTo("50.00");
    }
}
