package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.psp.PspChargeResult;
import com.kira.payment.paymentlinkbe.domain.psp.PspClient;
import com.kira.payment.paymentlinkbe.domain.psp.PspRoutingException;
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
        PspClient adyen = mock(PspClient.class);

        when(stripe.code()).thenReturn("STRIPE");
        when(adyen.code()).thenReturn("ADYEN");

        PspChargeResult result = new PspChargeResult(
                "STRIPE", "STRIPE_ref", true, "CAPTURED",
                new BigDecimal("100.00"), "USD"
        );

        when(stripe.charge("token", new BigDecimal("100.00"), "USD"))
                .thenReturn(result);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when
        PspChargeResult out = orchestrator.processPayment(
                "token", new BigDecimal("100.00"), "USD", null
        );

        // then
        assertThat(out.pspCode()).isEqualTo("STRIPE");
        verify(stripe).charge("token", new BigDecimal("100.00"), "USD");
        verify(adyen, never()).charge(anyString(), any(), anyString());
    }


    @Test
    void processPayment_shouldFailoverToSecondaryWhenPrimaryFails() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen = mock(PspClient.class);

        when(stripe.code()).thenReturn("STRIPE");
        when(adyen.code()).thenReturn("ADYEN");

        when(stripe.charge(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Stripe down"));

        PspChargeResult adyenResult = new PspChargeResult(
                "ADYEN", "ADYEN_ref", true, "CAPTURED",
                new BigDecimal("100.00"), "USD"
        );

        when(adyen.charge("token", new BigDecimal("100.00"), "USD"))
                .thenReturn(adyenResult);

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // when
        PspChargeResult out = orchestrator.processPayment(
                "token", new BigDecimal("100.00"), "USD", null
        );

        // then
        assertThat(out.pspCode()).isEqualTo("ADYEN");
        verify(stripe).charge(anyString(), any(), anyString());
        verify(adyen).charge("token", new BigDecimal("100.00"), "USD");
    }

    @Test
    void processPayment_shouldThrowWhenBothPspsFail() {
        // given
        PspClient stripe = mock(PspClient.class);
        PspClient adyen = mock(PspClient.class);

        when(stripe.code()).thenReturn("STRIPE");
        when(adyen.code()).thenReturn("ADYEN");

        when(stripe.charge(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Stripe down"));
        when(adyen.charge(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Adyen down"));

        Map<String, PspClient> clients = new HashMap<>();
        clients.put("stripeClient", stripe);
        clients.put("adyenClient", adyen);

        PspOrchestratorService orchestrator = new PspOrchestratorService(clients);

        // expect
        assertThatThrownBy(() -> orchestrator.processPayment(
                "token", new BigDecimal("100.00"), "USD", null
        )).isInstanceOf(PspRoutingException.class);
    }
}
