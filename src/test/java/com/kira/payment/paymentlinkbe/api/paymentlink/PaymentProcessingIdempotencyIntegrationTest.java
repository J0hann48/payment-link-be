package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.api.psp.TokenizeCardRequest;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkApplicationService;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.domain.psp.CardTokenResult;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.Payment;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentFeeRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "payment-link.public-base-url=http://localhost/checkout",
                "payment-link.default-psp=STRIPE",
                "fx.enabled=false"
        }
)
@AutoConfigureMockMvc
class PaymentProcessingIdempotencyIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentLinkApplicationService paymentLinkApplicationService;

    private PaymentLink paymentLink;
    private String pspToken;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        merchantFeeConfigRepository.deleteAll();
        merchantRepository.deleteAll();

        // 1) Merchant
        Merchant merchant = Merchant.builder()
                .externalId("m_ext_1")
                .name("Idempotency Merchant")
                .email("merchant@example.com")
                .defaultCurrency("USD")
                .createdAt(LocalDateTime.now())
                .build();
        merchant = merchantRepository.save(merchant);
        MerchantFeeConfig config = MerchantFeeConfig.builder()
                .merchant(merchant)
                .name("Default config")
                .currency("USD")
                .fixedFee(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
        merchantFeeConfigRepository.save(config);

        // 3) PaymentLink
        paymentLink = PaymentLink.builder()
                .publicId(UUID.randomUUID().toString())
                .slug("idem-" + UUID.randomUUID().toString().substring(0, 8))
                .merchant(merchant)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentLinkStatus.CREATED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        paymentLink = paymentLinkRepository.save(paymentLink);
        TokenizeCardRequest tokenizeRequest = new TokenizeCardRequest(
                "4111111111111111",
                12,
                Year.now().getValue() + 1,
                "123"
        );

        CardTokenResult tokenResult =
                paymentLinkApplicationService.tokenizeForCheckout(paymentLink.getSlug(), tokenizeRequest);
        this.pspToken = tokenResult.token().token();
    }

    @Test
    void processPayment_shouldBeIdempotentWithSameKey() throws Exception {
        String idempotencyKey = "test-idem-key-123";

        String bodyTemplate = """
                {
                  "pspToken": "%s",
                  "pspHint": null
                }
                """;
        String body = bodyTemplate.formatted(pspToken);

        // Primera llamada
        mockMvc.perform(post("/api/payment-links/{slug}/pay", paymentLink.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(body))
                .andExpect(status().isOk());

        // Segunda llamada con la misma key
        mockMvc.perform(post("/api/payment-links/{slug}/pay", paymentLink.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(body))
                .andExpect(status().isOk());

        // Sólo un Payment creado
        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).hasSize(1);

        Payment payment = payments.get(0);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
}
