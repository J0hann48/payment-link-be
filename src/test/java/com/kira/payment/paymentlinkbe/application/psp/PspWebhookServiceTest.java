package com.kira.payment.paymentlinkbe.application.psp;

import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PspWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentLinkRepository paymentLinkRepository;

    @InjectMocks
    private PspWebhookService pspWebhookService;

    @Test
    void handleStripeWebhook_shouldUpdatePaymentAndPaymentLinkWhenFound() {
        // given
        String pspReference = "STRIPE_123";
        String status = "CAPTURED";

        PaymentLink paymentLink = new PaymentLink();
        paymentLink.setStatus(PaymentLinkStatus.CREATED);

        Payment payment = new Payment();
        payment.setPspReference(pspReference);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentLink(paymentLink);
        payment.setUpdatedAt(LocalDateTime.now().minusMinutes(5));

        when(paymentRepository.findByPspReference(pspReference))
                .thenReturn(Optional.of(payment));

        // when
        pspWebhookService.handleStripeWebhook(pspReference, status);

        // then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(savedPayment.getUpdatedAt()).isNotNull();

        ArgumentCaptor<PaymentLink> linkCaptor = ArgumentCaptor.forClass(PaymentLink.class);
        verify(paymentLinkRepository).save(linkCaptor.capture());
        PaymentLink savedLink = linkCaptor.getValue();

        assertThat(savedLink.getStatus()).isEqualTo(PaymentLinkStatus.PAID);
    }

    @Test
    void handleStripeWebhook_shouldDoNothingWhenPaymentNotFound() {
        // given
        when(paymentRepository.findByPspReference("UNKNOWN"))
                .thenReturn(Optional.empty());

        // when
        pspWebhookService.handleStripeWebhook("UNKNOWN", "CAPTURED");

        // then
        verify(paymentRepository, never()).save(any());
        verify(paymentLinkRepository, never()).save(any());
    }

    @Test
    void handleAdyenWebhook_shouldMarkFailedWhenNotSuccess() {
        // given
        String pspReference = "ADYEN_123";

        PaymentLink paymentLink = new PaymentLink();
        paymentLink.setStatus(PaymentLinkStatus.CREATED);

        Payment payment = new Payment();
        payment.setPspReference(pspReference);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentLink(paymentLink);

        when(paymentRepository.findByPspReference(pspReference))
                .thenReturn(Optional.of(payment));

        // when
        pspWebhookService.handleAdyenWebhook(pspReference, "AUTHORISATION", false);

        // then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentLinkRepository, never()).save(any());
    }
}
