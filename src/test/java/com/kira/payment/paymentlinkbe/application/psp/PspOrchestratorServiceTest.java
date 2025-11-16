package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.psp.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PspOrchestratorServiceTest {

    @Test
    void processPayment_shouldUsePrimaryPspWhenSuccess() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen  = mock(PspClient.class);

        when(stripe.getCode()).thenReturn(PspCode.STRIPE);
        when(adyen.getCode()).thenReturn(PspCode.ADYEN);

        PspChargeResult stripeResult = PspChargeResult.success(
                "ch_stripe_123",
                new BigDecimal("100.00"),
                "USD"
        );

        when(stripe.charge(any(PspChargeRequest.class)))
                .thenReturn(stripeResult);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when
        RoutedPspChargeResult routed = orchestrator.processPayment(
                "token",
                new BigDecimal("100.00"),
                "USD",
                null // sin hint → STRIPE primario
        );

        // then
        assertThat(routed.pspCode()).isEqualTo(PspCode.STRIPE);
        assertThat(routed.result()).isSameAs(stripeResult);

        verify(stripe, times(1)).charge(any(PspChargeRequest.class));
        verify(adyen, never()).charge(any(PspChargeRequest.class));
    }

    @Test
    void processPayment_shouldFailoverToSecondaryWhenPrimaryThrowsException() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen  = mock(PspClient.class);

        when(stripe.getCode()).thenReturn(PspCode.STRIPE);
        when(adyen.getCode()).thenReturn(PspCode.ADYEN);

        when(stripe.charge(any(PspChargeRequest.class)))
                .thenThrow(new RuntimeException("Stripe down"));

        PspChargeResult adyenResult = PspChargeResult.success(
                "ch_adyen_123",
                new BigDecimal("100.00"),
                "USD"
        );

        when(adyen.charge(any(PspChargeRequest.class)))
                .thenReturn(adyenResult);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when
        RoutedPspChargeResult routed = orchestrator.processPayment(
                "token",
                new BigDecimal("100.00"),
                "USD",
                null
        );

        // then
        assertThat(routed.pspCode()).isEqualTo(PspCode.ADYEN);
        assertThat(routed.result()).isSameAs(adyenResult);

        verify(stripe, times(1)).charge(any(PspChargeRequest.class));
        verify(adyen, times(1)).charge(any(PspChargeRequest.class));
    }

    @Test
    void processPayment_shouldFailoverToSecondaryWhenPrimaryReturnsFailed() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen  = mock(PspClient.class);

        when(stripe.getCode()).thenReturn(PspCode.STRIPE);
        when(adyen.getCode()).thenReturn(PspCode.ADYEN);

        // Primario devuelve FAILED (sin excepción)
        PspChargeResult failedStripe = PspChargeResult.failure(
                "ch_stripe_failed",
                "ERR",
                "Stripe failure"
        );
        when(stripe.charge(any(PspChargeRequest.class)))
                .thenReturn(failedStripe);

        // Secundario devuelve SUCCEEDED
        PspChargeResult adyenResult = PspChargeResult.success(
                "ch_adyen_123",
                new BigDecimal("100.00"),
                "USD"
        );
        when(adyen.charge(any(PspChargeRequest.class)))
                .thenReturn(adyenResult);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when
        RoutedPspChargeResult routed = orchestrator.processPayment(
                "token",
                new BigDecimal("100.00"),
                "USD",
                null
        );

        // then
        assertThat(routed.pspCode()).isEqualTo(PspCode.ADYEN);
        assertThat(routed.result()).isSameAs(adyenResult);

        verify(stripe, times(1)).charge(any(PspChargeRequest.class));
        verify(adyen, times(1)).charge(any(PspChargeRequest.class));
    }

    @Test
    void processPayment_shouldThrowWhenBothPspsReturnFailed() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen  = mock(PspClient.class);

        when(stripe.getCode()).thenReturn(PspCode.STRIPE);
        when(adyen.getCode()).thenReturn(PspCode.ADYEN);

        PspChargeResult failedStripe = PspChargeResult.failure(
                "ch_stripe_failed",
                "ERR_STRIPE",
                "Stripe failure"
        );
        PspChargeResult failedAdyen = PspChargeResult.failure(
                "ch_adyen_failed",
                "ERR_ADYEN",
                "Adyen failure"
        );

        when(stripe.charge(any(PspChargeRequest.class)))
                .thenReturn(failedStripe);
        when(adyen.charge(any(PspChargeRequest.class)))
                .thenReturn(failedAdyen);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // expect
        assertThatThrownBy(() -> orchestrator.processPayment(
                "token",
                new BigDecimal("100.00"),
                "USD",
                null
        )).isInstanceOf(PspRoutingException.class);

        verify(stripe, times(1)).charge(any(PspChargeRequest.class));
        verify(adyen, times(1)).charge(any(PspChargeRequest.class));
    }

    @Test
    void processPayment_shouldRespectPspHintAndUseAdyenAsPrimary() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen  = mock(PspClient.class);

        when(stripe.getCode()).thenReturn(PspCode.STRIPE);
        when(adyen.getCode()).thenReturn(PspCode.ADYEN);

        PspChargeResult adyenResult = PspChargeResult.success(
                "ch_adyen_123",
                new BigDecimal("100.00"),
                "USD"
        );
        when(adyen.charge(any(PspChargeRequest.class)))
                .thenReturn(adyenResult);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when - hint ADYEN → primario ADYEN
        RoutedPspChargeResult routed = orchestrator.processPayment(
                "token",
                new BigDecimal("100.00"),
                "USD",
                "ADYEN"
        );

        // then
        assertThat(routed.pspCode()).isEqualTo(PspCode.ADYEN);
        assertThat(routed.result()).isSameAs(adyenResult);

        // Se llama solo a ADYEN, no a STRIPE
        verify(adyen, times(1)).charge(any(PspChargeRequest.class));
        verify(stripe, never()).charge(any(PspChargeRequest.class));
    }
}
