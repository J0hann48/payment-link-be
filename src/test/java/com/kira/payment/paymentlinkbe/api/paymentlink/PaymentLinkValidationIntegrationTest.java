package com.kira.payment.paymentlinkbe.api.paymentlink;


import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fx.FxRateSnapshotRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.*;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLinkRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.PspRoutingRuleRepository;
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
class PaymentLinkValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    // --- Repos adicionales para limpiar FKs ---

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private PaymentFeeRepository paymentFeeRepository;

    @Autowired(required = false)
    private PaymentIncentiveRepository paymentIncentiveRepository;

    @Autowired(required = false)
    private FxRateSnapshotRepository fxRateSnapshotRepository;

    @Autowired
    private RecipientRepository recipientRepository;

    @Autowired
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    @Autowired(required = false)
    private PspRoutingRuleRepository pspRoutingRuleRepository;

    @Autowired(required = false)
    private IncentiveRuleRepository incentiveRuleRepository;

    @Autowired(required = false)
    private WebhookEventRepository webhookEventRepository;

    private PaymentLink paymentLink;

    @BeforeEach
    void setUp() {
        // 1) Hojas que cuelgan de payment
        if (paymentFeeRepository != null) {
            paymentFeeRepository.deleteAll();
        }
        if (paymentIncentiveRepository != null) {
            paymentIncentiveRepository.deleteAll();
        }
        if (fxRateSnapshotRepository != null) {
            fxRateSnapshotRepository.deleteAll();
        }
        paymentRepository.deleteAll();

        // 2) Eventos de webhook (no tienen FK pero así mantenemos el entorno limpio)
        if (webhookEventRepository != null) {
            webhookEventRepository.deleteAll();
        }

        // 3) Links de pago
        paymentLinkRepository.deleteAll();

        // 4) Reglas de routing / incentivos que referencian merchant
        if (pspRoutingRuleRepository != null) {
            pspRoutingRuleRepository.deleteAll();
        }
        if (incentiveRuleRepository != null) {
            incentiveRuleRepository.deleteAll();
        }

        // 5) Recipients y config de fees (también referencian merchant)
        recipientRepository.deleteAll();
        merchantFeeConfigRepository.deleteAll();

        // 6) Merchants al final
        merchantRepository.deleteAll();

        // ------- Crear datos para los tests --------

        Merchant merchant = new Merchant();
        merchant.setExternalId("ext-val-" + UUID.randomUUID());
        merchant.setName("Validation Merchant");
        merchant.setEmail("validation-merchant@example.com");
        merchant.setDefaultCurrency("USD");
        merchant.setCreatedAt(LocalDateTime.now());
        merchant = merchantRepository.save(merchant);

        // (Opcional pero recomendable para mantener coherente el dominio;
        // no se usa en estos tests porque fallan por @Valid antes de llegar al fee engine)
        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setMerchant(merchant);
        config.setName("Validation config");
        config.setCurrency("USD");
        config.setFixedFee(new BigDecimal("1.00"));
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFxMarkupPct(new BigDecimal("0.01"));
        config.setCreatedAt(LocalDateTime.now());
        merchantFeeConfigRepository.save(config);

        paymentLink = new PaymentLink();
        paymentLink.setPublicId(UUID.randomUUID().toString());
        paymentLink.setSlug("val-" + UUID.randomUUID().toString().substring(0, 8));
        paymentLink.setMerchant(merchant);
        paymentLink.setAmount(new BigDecimal("50.00"));
        paymentLink.setCurrency("USD");
        paymentLink.setStatus(PaymentLinkStatus.CREATED);
        paymentLink.setCreatedAt(LocalDateTime.now());
        paymentLink.setUpdatedAt(LocalDateTime.now());
        paymentLinkRepository.save(paymentLink);
    }

    @Test
    void tokenize_shouldReturn400_whenCardNumberTooShort() throws Exception {
        // Ojo: quita el comentario // del JSON, eso no es JSON válido.
        String body = """
                {
                  "cardNumber": "123456789",
                  "expMonth": 12,
                  "expYear": 2030,
                  "cvc": "123"
                }
                """;

        mockMvc.perform(
                        post("/api/psp/stripe/tokenize")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void pay_shouldReturn400_whenPspTokenBlank() throws Exception {
        String body = """
                {
                  "pspToken": "",
                  "pspHint": null
                }
                """;

        mockMvc.perform(
                        post("/api/payment-links/{slug}/pay", paymentLink.getSlug())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }
}
