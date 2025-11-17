package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "payment-link.public-base-url=http://localhost/checkout",
                "payment-link.default-psp=STRIPE",
                "fx.enabled=false"
        }
)
@AutoConfigureMockMvc
class PaymentProcessingExpiredLinkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    private PaymentLink expiredLink;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        merchantFeeConfigRepository.deleteAll();
        merchantRepository.deleteAll();

        Merchant merchant = Merchant.builder()
                .externalId("m_ext_expired")
                .name("Expired Merchant")
                .email("merchant-expired@example.com")
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

        expiredLink = PaymentLink.builder()
                .publicId(UUID.randomUUID().toString())
                .slug("exp-" + UUID.randomUUID().toString().substring(0, 8))
                .merchant(merchant)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentLinkStatus.CREATED)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build();

        expiredLink = paymentLinkRepository.save(expiredLink);
    }

    @Test
    void processPayment_shouldReturn400WhenPaymentLinkExpired() throws Exception {
        String body = """
                {
                  "pspToken": "sim_stripe_failed",
                  "pspHint": null
                }
                """;

        mockMvc.perform(post("/api/payment-links/{slug}/pay", expiredLink.getSlug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_LINK_STATE"));
    }
}
