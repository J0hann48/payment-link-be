package com.kira.payment.paymentlinkbe.infraestructure.persistence.stripe;

import com.kira.payment.paymentlinkbe.domain.psp.*;
import com.kira.payment.paymentlinkbe.infraestructure.psp.stripe.StripePspClientMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StripeMockClientTest {
    @Mock
    private PspWebhookPublisher webhookPublisher;

    @InjectMocks
    private StripePspClientMock stripeMockClient;

    @Test
    void tokenizeCard_shouldReturnTokenAndStoreIt() {
        // given
        PspTokenizationRequest request = new PspTokenizationRequest(
                "4111111111111111",
                12,
                2030,
                "123"
        );

        // when
        CardToken token = stripeMockClient.tokenizeCard(request);

        // then
        assertThat(token).isNotNull();
        assertThat(token.token()).isNotBlank();
        assertThat(token.last4()).isEqualTo("1111");
        assertThat(token.brand()).isEqualTo("VISA");
        assertThat(token.createdAt()).isNotNull();
    }

    @Test
    void charge_shouldSucceedWithValidToken_andPublishSucceededWebhook() {
        // given
        PspTokenizationRequest request = new PspTokenizationRequest(
                "4111111111111111",
                12,
                2030,
                "123"
        );
        CardToken token = stripeMockClient.tokenizeCard(request);

        PspChargeRequest chargeRequest = new PspChargeRequest(
                token.token(),
                new BigDecimal("100.00"),
                "USD"
        );

        // when
        PspChargeResult result = stripeMockClient.charge(chargeRequest);

        // then
        assertThat(result.status()).isEqualTo(ChargeStatus.SUCCEEDED);
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.failureCode()).isNull();
        assertThat(result.failureMessage()).isNull();

        // verificar webhook
        ArgumentCaptor<String> pspChargeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookPublisher).publishChargeSucceeded(
                eq(PspCode.STRIPE),
                pspChargeIdCaptor.capture(),
                anyString() // paymentId fake generado dentro del mock
        );

        assertThat(pspChargeIdCaptor.getValue()).isEqualTo(result.pspChargeId());
    }

    @Test
    void charge_shouldFailWithInvalidToken_andPublishFailedWebhook() {
        // given
        PspChargeRequest chargeRequest = new PspChargeRequest(
                "non_existing_token",
                new BigDecimal("50.00"),
                "USD"
        );

        // when
        PspChargeResult result = stripeMockClient.charge(chargeRequest);

        // then
        assertThat(result.status()).isEqualTo(ChargeStatus.FAILED);
        assertThat(result.amount()).isNull();
        assertThat(result.currency()).isNull();
        assertThat(result.failureCode()).isEqualTo("INVALID_TOKEN");
        assertThat(result.failureMessage()).contains("Card token not found");

        // verificar webhook FAILED
        ArgumentCaptor<String> pspChargeIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> failureCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> failureMessageCaptor = ArgumentCaptor.forClass(String.class);

        verify(webhookPublisher).publishChargeFailed(
                eq(PspCode.STRIPE),
                pspChargeIdCaptor.capture(),
                anyString(), // paymentId mock
                failureCodeCaptor.capture(),
                failureMessageCaptor.capture()
        );

        assertThat(pspChargeIdCaptor.getValue()).isEqualTo(result.pspChargeId());
        assertThat(failureCodeCaptor.getValue()).isEqualTo("INVALID_TOKEN");
        assertThat(failureMessageCaptor.getValue()).contains("Card token not found");
    }
}
