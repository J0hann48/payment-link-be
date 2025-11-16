package com.kira.payment.paymentlinkbe.infraestructure.persistence.adyen;

import com.kira.payment.paymentlinkbe.domain.psp.*;
import com.kira.payment.paymentlinkbe.infraestructure.psp.adyen.AdyenPspClientMock;
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
class AdyenMockClientTest {

    @Mock
    private PspWebhookPublisher webhookPublisher;

    @InjectMocks
    private AdyenPspClientMock adyenMockClient;

    @Test
    void tokenizeCard_shouldReturnTokenAndStoreIt() {
        // given
        PspTokenizationRequest request = new PspTokenizationRequest(
                "5555555555554444",
                11,
                2031,
                "456"
        );

        // when
        CardToken token = adyenMockClient.tokenizeCard(request);

        // then
        assertThat(token).isNotNull();
        assertThat(token.token()).isNotBlank();
        assertThat(token.last4()).isEqualTo("4444");
        assertThat(token.brand()).isEqualTo("MASTERCARD");
        assertThat(token.createdAt()).isNotNull();
    }

    @Test
    void charge_shouldSucceedWithValidToken_andPublishSucceededWebhook() {
        // given
        PspTokenizationRequest request = new PspTokenizationRequest(
                "5555555555554444",
                11,
                2031,
                "456"
        );
        CardToken token = adyenMockClient.tokenizeCard(request);

        PspChargeRequest chargeRequest = new PspChargeRequest(
                token.token(),
                new BigDecimal("200.00"),
                "EUR"
        );

        // when
        PspChargeResult result = adyenMockClient.charge(chargeRequest);

        // then
        assertThat(result.status()).isEqualTo(ChargeStatus.SUCCEEDED);
        assertThat(result.amount()).isEqualByComparingTo("200.00");
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.failureCode()).isNull();
        assertThat(result.failureMessage()).isNull();

        // verificar webhook
        ArgumentCaptor<String> pspChargeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookPublisher).publishChargeSucceeded(
                eq(PspCode.ADYEN),
                pspChargeIdCaptor.capture(),
                anyString()
        );

        assertThat(pspChargeIdCaptor.getValue()).isEqualTo(result.pspChargeId());
    }

    @Test
    void charge_shouldFailWithInvalidToken_andPublishFailedWebhook() {
        // given
        PspChargeRequest chargeRequest = new PspChargeRequest(
                "non_existing_token",
                new BigDecimal("80.00"),
                "EUR"
        );

        // when
        PspChargeResult result = adyenMockClient.charge(chargeRequest);

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
                eq(PspCode.ADYEN),
                pspChargeIdCaptor.capture(),
                anyString(),
                failureCodeCaptor.capture(),
                failureMessageCaptor.capture()
        );

        assertThat(pspChargeIdCaptor.getValue()).isEqualTo(result.pspChargeId());
        assertThat(failureCodeCaptor.getValue()).isEqualTo("INVALID_TOKEN");
        assertThat(failureMessageCaptor.getValue()).contains("Card token not found");
    }
}
