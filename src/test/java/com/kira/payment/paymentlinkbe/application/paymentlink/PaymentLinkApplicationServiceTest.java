package com.kira.payment.paymentlinkbe.application.paymentlink;

import com.kira.payment.paymentlinkbe.application.psp.PspOrchestratorService;
import com.kira.payment.paymentlinkbe.domain.fee.FeeBreakdown;
import com.kira.payment.paymentlinkbe.domain.fee.FeeEngine;
import com.kira.payment.paymentlinkbe.domain.merchant.MerchantNotFoundException;
import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.domain.psp.PspChargeResult;
import com.kira.payment.paymentlinkbe.domain.psp.PspClient;
import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import com.kira.payment.paymentlinkbe.domain.psp.RoutedPspChargeResult;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Recipient;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.Psp;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.PspRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLinkApplicationServiceTest {

    @Mock
    private PaymentLinkRepository paymentLinkRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RecipientRepository recipientRepository;

    @Mock
    private FeeEngine feeEngine;

    @Mock
    private PspOrchestratorService pspOrchestratorService;

    @Mock
    private PspRepository pspRepository;

    @Mock
    private Map<String, PspClient> pspClients;

    @InjectMocks
    private PaymentLinkApplicationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://checkout.test");
        ReflectionTestUtils.setField(service, "defaultPspCode", "STRIPE");
    }

    @Test
    void createPaymentLink_shouldCreateLinkAndReturnView() {
        // given
        Long merchantId = 1L;
        Long recipientId = 10L;
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        Merchant merchant = new Merchant();
        merchant.setId(merchantId);

        Recipient recipient = new Recipient();
        recipient.setId(recipientId);

        when(merchantRepository.findById(merchantId))
                .thenReturn(Optional.of(merchant));
        when(recipientRepository.findById(recipientId))
                .thenReturn(Optional.of(recipient));

        when(paymentLinkRepository.existsBySlug(anyString()))
                .thenReturn(false);

        PaymentLink savedLink = new PaymentLink();
        savedLink.setId(123L);
        savedLink.setPublicId(UUID.randomUUID().toString());
        savedLink.setSlug("abc123xyz0");
        savedLink.setMerchant(merchant);
        savedLink.setRecipient(recipient);
        savedLink.setAmount(amount);
        savedLink.setCurrency(currency);
        savedLink.setDescription("Test payment");
        savedLink.setStatus(PaymentLinkStatus.CREATED);
        savedLink.setCreatedAt(LocalDateTime.now());
        savedLink.setUpdatedAt(savedLink.getCreatedAt());

        when(paymentLinkRepository.save(any(PaymentLink.class)))
                .thenReturn(savedLink);

        FeeBreakdown breakdown = new FeeBreakdown(
                amount, new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                new BigDecimal("4.00"),
                new BigDecimal("96.00"),
                currency
        );
        when(feeEngine.calculateForPaymentLink(merchantId, recipientId, amount, currency))
                .thenReturn(breakdown);

        CreatePaymentLinkCommand command = new CreatePaymentLinkCommand(
                merchantId,
                recipientId,
                amount,
                currency,
                "Test payment",
                null
        );

        // when
        PaymentLinkView view = service.createPaymentLink(command);

        // then
        assertThat(view.id()).isEqualTo(123L);
        assertThat(view.merchantId()).isEqualTo(merchantId);
        assertThat(view.recipientId()).isEqualTo(recipientId);
        assertThat(view.amount()).isEqualByComparingTo("100.00");
        assertThat(view.feeBreakdown()).isEqualTo(breakdown);
        assertThat(view.slug()).isNotBlank();
        assertThat(view.checkoutUrl()).contains(view.slug());

        verify(paymentLinkRepository).save(any(PaymentLink.class));
        verify(feeEngine).calculateForPaymentLink(merchantId, recipientId, amount, currency);
    }

    @Test
    void createPaymentLink_shouldThrowWhenMerchantNotFound() {
        // given
        Long merchantId = 1L;
        CreatePaymentLinkCommand command = new CreatePaymentLinkCommand(
                merchantId,
                null,
                new BigDecimal("50.00"),
                "USD",
                "Test",
                null
        );

        when(merchantRepository.findById(merchantId))
                .thenReturn(Optional.empty());

        // expect
        assertThatThrownBy(() -> service.createPaymentLink(command))
                .isInstanceOf(MerchantNotFoundException.class);
    }

    @Test
    void getPaymentLink_shouldReturnViewWhenFound() {
        // given
        String slug = "abc123xyz0";
        Merchant merchant = new Merchant();
        merchant.setId(1L);

        PaymentLink link = new PaymentLink();
        link.setId(123L);
        link.setSlug(slug);
        link.setMerchant(merchant);
        link.setAmount(new BigDecimal("100.00"));
        link.setCurrency("USD");
        link.setDescription("Test payment");
        link.setStatus(PaymentLinkStatus.CREATED);
        link.setCreatedAt(LocalDateTime.now());

        when(paymentLinkRepository.findBySlug(slug))
                .thenReturn(Optional.of(link));

        FeeBreakdown breakdown = new FeeBreakdown(
                new BigDecimal("100.00"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                new BigDecimal("4.00"),
                new BigDecimal("96.00"),
                "USD"
        );
        when(feeEngine.calculateForPaymentLink(1L, null,
                new BigDecimal("100.00"), "USD"))
                .thenReturn(breakdown);

        // when
        PaymentLinkView view = service.getPaymentLink(slug);

        // then
        assertThat(view.id()).isEqualTo(123L);
        assertThat(view.slug()).isEqualTo(slug);
        assertThat(view.feeBreakdown()).isEqualTo(breakdown);
    }

    @Test
    void getPaymentLink_shouldThrowWhenNotFound() {
        when(paymentLinkRepository.findBySlug("unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPaymentLink("unknown"))
                .isInstanceOf(PaymentLinkNotFoundException.class);
    }

    @Test
    void processPayment_shouldCreatePaymentAndUpdateLinkOnSuccess() {
        // given
        String slug = "pay123";
        Merchant merchant = new Merchant();
        merchant.setId(1L);

        PaymentLink link = new PaymentLink();
        link.setId(10L);
        link.setSlug(slug);
        link.setMerchant(merchant);
        link.setAmount(new BigDecimal("100.00"));
        link.setCurrency("USD");
        link.setStatus(PaymentLinkStatus.CREATED);

        when(paymentLinkRepository.findBySlug(slug))
                .thenReturn(Optional.of(link));

        FeeBreakdown breakdown = new FeeBreakdown(
                new BigDecimal("100.00"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                new BigDecimal("4.00"),
                new BigDecimal("96.00"),
                "USD"
        );
        when(feeEngine.calculateForPaymentLink(1L, null,
                new BigDecimal("100.00"), "USD"))
                .thenReturn(breakdown);

        PspChargeResult pspResult = PspChargeResult.success(
                "psp_ch_123",
                new BigDecimal("100.00"),
                "USD"
        );
        RoutedPspChargeResult routed = new RoutedPspChargeResult(
                PspCode.STRIPE,
                pspResult
        );

        when(pspOrchestratorService.processPayment(
                eq("token123"),
                eq(new BigDecimal("100.00")),
                eq("USD"),
                eq("STRIPE")
        )).thenReturn(routed);

        Psp pspEntity = new Psp();
        pspEntity.setCode(PspCode.STRIPE);
        when(pspRepository.findByCode(PspCode.STRIPE))
                .thenReturn(Optional.of(pspEntity));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    p.setId(999L);
                    return p;
                });

        ProcessPaymentCommand command = new ProcessPaymentCommand("token123", null);

        // when
        ProcessPaymentResult result = service.processPayment(slug, command);

        // then
        assertThat(result.paymentId()).isEqualTo(999L);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(result.pspUsed()).isEqualTo("STRIPE");
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.feeBreakdown()).isEqualTo(breakdown);

        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.PAID);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(savedPayment.getPspReference()).isEqualTo("psp_ch_123");
    }

    @Test
    void processPayment_shouldThrowWhenLinkNotPayable() {
        String slug = "pay123";
        PaymentLink link = new PaymentLink();
        link.setSlug(slug);
        link.setStatus(PaymentLinkStatus.PAID);

        when(paymentLinkRepository.findBySlug(slug))
                .thenReturn(Optional.of(link));

        ProcessPaymentCommand command = new ProcessPaymentCommand("token", null);

        assertThatThrownBy(() -> service.processPayment(slug, command))
                .isInstanceOf(PaymentLinkInvalidStateException.class);
    }

    @Test
    void getPaymentLink_shouldExpireLinkWhenExpiredInPast() {
        // given
        String slug = "expired123";
        Merchant merchant = new Merchant();
        merchant.setId(1L);

        PaymentLink link = new PaymentLink();
        link.setId(123L);
        link.setSlug(slug);
        link.setMerchant(merchant);
        link.setAmount(new BigDecimal("100.00"));
        link.setCurrency("USD");
        link.setDescription("Expired payment");
        link.setStatus(PaymentLinkStatus.CREATED);
        link.setCreatedAt(LocalDateTime.now().minusDays(10));
        link.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(paymentLinkRepository.findBySlug(slug))
                .thenReturn(Optional.of(link));

        FeeBreakdown breakdown = new FeeBreakdown(
                new BigDecimal("100.00"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                new BigDecimal("4.00"),
                new BigDecimal("96.00"),
                "USD"
        );
        when(feeEngine.calculateForPaymentLink(
                1L,
                null,
                new BigDecimal("100.00"),
                "USD"
        )).thenReturn(breakdown);

        // when
        PaymentLinkView view = service.getPaymentLink(slug);

        // then
        assertThat(view.id()).isEqualTo(123L);
        assertThat(view.slug()).isEqualTo(slug);
        assertThat(view.feeBreakdown()).isEqualTo(breakdown);
        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.EXPIRED);
    }

    @Test
    void processPayment_shouldCreateFailedPaymentAndNotMarkLinkAsPaidWhenPspFails() {
        // given
        String slug = "payFail123";
        Merchant merchant = new Merchant();
        merchant.setId(1L);

        PaymentLink link = new PaymentLink();
        link.setId(10L);
        link.setSlug(slug);
        link.setMerchant(merchant);
        link.setAmount(new BigDecimal("100.00"));
        link.setCurrency("USD");
        link.setStatus(PaymentLinkStatus.CREATED);

        when(paymentLinkRepository.findBySlug(slug))
                .thenReturn(Optional.of(link));

        FeeBreakdown breakdown = new FeeBreakdown(
                new BigDecimal("100.00"),
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                new BigDecimal("4.00"),
                new BigDecimal("96.00"),
                "USD"
        );
        when(feeEngine.calculateForPaymentLink(
                1L,
                null,
                new BigDecimal("100.00"),
                "USD"
        )).thenReturn(breakdown);

        PspChargeResult failedResult = PspChargeResult.failure(
                "psp_ch_failed_123",
                "ERR_GENERIC",
                "Generic PSP error"
        );
        RoutedPspChargeResult routed = new RoutedPspChargeResult(
                PspCode.STRIPE,
                failedResult
        );
        when(pspOrchestratorService.processPayment(
                eq("token123"),
                eq(new BigDecimal("100.00")),
                eq("USD"),
                eq("STRIPE")
        )).thenReturn(routed);

        Psp pspEntity = new Psp();
        pspEntity.setCode(PspCode.STRIPE);
        when(pspRepository.findByCode(PspCode.STRIPE))
                .thenReturn(Optional.of(pspEntity));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    p.setId(1000L);
                    return p;
                });

        ProcessPaymentCommand command = new ProcessPaymentCommand("token123", null);

        // when
        ProcessPaymentResult result = service.processPayment(slug, command);

        // then
        assertThat(result.paymentId()).isEqualTo(1000L);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.feeBreakdown()).isEqualTo(breakdown);
        assertThat(result.pspUsed()).isEqualTo("STRIPE");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedPayment.getPspReference()).isEqualTo("psp_ch_failed_123");
        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.CREATED);
    }

}
