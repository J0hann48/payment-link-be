package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fx.FxRateSnapshotRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.MerchantRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.RecipientRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.IncentiveRuleRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentFeeRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentIncentiveRepository;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.payment.PaymentRepository;
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
class PaymentLinkFailoverIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

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

    @Autowired
    private ObjectMapper objectMapper;

    private Merchant merchant;
    private PaymentLink paymentLink;

    @BeforeEach
    void setUp() {
        // 1) Tablas hoja que dependen de payment
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

        // 2) payment_link (depende de merchant/recipient)
        paymentLinkRepository.deleteAll();

        // 3) reglas/routing/incentivos que dependen de merchant
        if (pspRoutingRuleRepository != null) {
            pspRoutingRuleRepository.deleteAll();
        }
        if (incentiveRuleRepository != null) {
            incentiveRuleRepository.deleteAll();
        }

        // 4) recipient (FK a merchant)
        recipientRepository.deleteAll();

        // 5) fee config (FK a merchant)
        merchantFeeConfigRepository.deleteAll();

        // 6) merchant al final
        merchantRepository.deleteAll();

        // --- Crear datos para el test ---

        merchant = new Merchant();
        merchant.setExternalId("ext-it-" + UUID.randomUUID());
        merchant.setName("IT Merchant");
        merchant.setEmail("it-merchant@example.com");
        merchant.setDefaultCurrency("USD");
        merchant.setCreatedAt(LocalDateTime.now());
        merchant = merchantRepository.save(merchant);

        // Config de fees para que DefaultFeeEngine funcione
        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setMerchant(merchant);
        config.setName("IT default config");
        config.setCurrency("USD");
        config.setFixedFee(new BigDecimal("1.00"));
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFxMarkupPct(new BigDecimal("0.01"));
        config.setCreatedAt(LocalDateTime.now());
        merchantFeeConfigRepository.save(config);

        paymentLink = new PaymentLink();
        paymentLink.setPublicId(UUID.randomUUID().toString());
        paymentLink.setSlug("it-" + UUID.randomUUID().toString().substring(0, 8));
        paymentLink.setMerchant(merchant);
        paymentLink.setAmount(new BigDecimal("100.00"));
        paymentLink.setCurrency("USD");
        paymentLink.setStatus(PaymentLinkStatus.CREATED);
        paymentLink.setCreatedAt(LocalDateTime.now());
        paymentLink.setUpdatedAt(LocalDateTime.now());
        paymentLink = paymentLinkRepository.save(paymentLink);
    }

    @Test
    void pay_shouldFailoverToSecondaryPspAndSucceed_whenPrimaryCannotUseToken() throws Exception {
        String tokenizeBody = """
            {
              "cardNumber": "5555444433331111",
              "expMonth": 12,
              "expYear": 2030,
              "cvc": "123"
            }
            """;

        var tokenResult = mockMvc.perform(
                        post("/api/psp/adyen/tokenize")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(tokenizeBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pspCode").value("ADYEN"))
                .andExpect(jsonPath("$.pspToken").isString())
                .andReturn();

        String tokenJson = tokenResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(tokenJson);

        // El campo correcto según tu CardTokenResponse
        String cardToken = root.get("pspToken").asText();

        String payBody = """
            {
              "pspToken": "%s",
              "pspHint": null
            }
            """.formatted(cardToken);

        mockMvc.perform(
                        post("/api/payment-links/{slug}/pay", paymentLink.getSlug())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.pspUsed").value("ADYEN"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }


    @Test
    void pay_shouldReturn502AndApiError_whenBothPspsFail() throws Exception {
        String payBody = """
                {
                  "pspToken": "sim_stripe_exception",
                  "pspHint": null
                }
                """;

        mockMvc.perform(
                        post("/api/payment-links/{slug}/pay", paymentLink.getSlug())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payBody)
                )
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.code").value("PSP_ROUTING_FAILED"))
                .andExpect(jsonPath("$.message").exists());
    }
}
