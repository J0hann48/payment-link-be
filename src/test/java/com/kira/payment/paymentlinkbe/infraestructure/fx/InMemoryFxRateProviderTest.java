package com.kira.payment.paymentlinkbe.infraestructure.fx;

import com.kira.payment.paymentlinkbe.domain.fx.FxQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryFxRateProviderTest {

    @Test
    void getQuote_shouldReturnBaseRateWithoutJitter_whenMaxJitterIsZero() {
        // given
        InMemoryFxRateProvider provider =
                new InMemoryFxRateProvider(BigDecimal.ZERO); // sin jitter
        provider.setBaseRate("USD", "MXN", new BigDecimal("18.00"));

        // when
        FxQuote quote = provider.getQuote("USD", "MXN");

        // then
        assertThat(quote.baseCurrency()).isEqualTo("USD");
        assertThat(quote.counterCurrency()).isEqualTo("MXN");
        assertThat(quote.baseRate()).isEqualByComparingTo("18.00");
        assertThat(quote.jitterApplied()).isEqualByComparingTo("0.000000");
        assertThat(quote.effectiveRate()).isEqualByComparingTo("18.000000");
        assertThat(quote.quotedAt()).isNotNull();
    }

    @Test
    void getQuote_shouldReturnOneForSameCurrency() {
        InMemoryFxRateProvider provider =
                new InMemoryFxRateProvider(new BigDecimal("0.01"));

        FxQuote quote = provider.getQuote("USD", "USD");

        assertThat(quote.baseRate()).isEqualByComparingTo("1.000000");
        assertThat(quote.effectiveRate()).isEqualByComparingTo("1.000000");
        assertThat(quote.jitterApplied()).isEqualByComparingTo("0.000000");
    }

    @Test
    void getQuote_shouldThrowWhenPairNotConfigured() {
        InMemoryFxRateProvider provider =
                new InMemoryFxRateProvider(BigDecimal.ZERO);

        assertThatThrownBy(() -> provider.getQuote("EUR", "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FX rate not configured");
    }
}
