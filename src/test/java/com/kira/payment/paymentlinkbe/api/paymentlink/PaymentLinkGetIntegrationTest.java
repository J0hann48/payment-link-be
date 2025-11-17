package com.kira.payment.paymentlinkbe.api.paymentlink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kira.payment.paymentlinkbe.domain.paymentlink.PaymentLinkStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfig;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.MerchantFeeConfigRepository;
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

@SpringBootTest(
        properties = {
                "payment-link.public-base-url=http://localhost/checkout",
                "payment-link.default-psp=STRIPE",
                "fx.enabled=false"
        }
)
@AutoConfigureMockMvc
class PaymentLinkGetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private MerchantFeeConfigRepository merchantFeeConfigRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private PaymentFeeRepository paymentFeeRepository;

    @Autowired(required = false)
    private PaymentIncentiveRepository paymentIncentiveRepository;

    @Autowired
    private RecipientRepository recipientRepository;

    @Autowired(required = false)
    private PspRoutingRuleRepository pspRoutingRuleRepository;

    @Autowired(required = false)
    private IncentiveRuleRepository incentiveRuleRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        if (paymentFeeRepository != null) {
            paymentFeeRepository.deleteAll();
        }
        if (paymentIncentiveRepository != null) {
            paymentIncentiveRepository.deleteAll();
        }
        paymentRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        if (pspRoutingRuleRepository != null) {
            pspRoutingRuleRepository.deleteAll();
        }
        recipientRepository.deleteAll();

        if (incentiveRuleRepository != null) {
            incentiveRuleRepository.deleteAll();
        }
        merchantFeeConfigRepository.deleteAll();
        merchantRepository.deleteAll();

        merchant = new Merchant();
        merchant.setExternalId("ext-" + UUID.randomUUID());
        merchant.setName("GetLink Merchant");
        merchant.setEmail("getlink@example.com");
        merchant.setDefaultCurrency("USD");
        merchant.setCreatedAt(LocalDateTime.now());

        merchant = merchantRepository.save(merchant);

        MerchantFeeConfig config = new MerchantFeeConfig();
        config.setMerchant(merchant);
        config.setName("Default config");
        config.setCurrency("USD");
        config.setFixedFee(new BigDecimal("1.00"));
        config.setPercentageFee(new BigDecimal("0.03"));
        config.setFxMarkupPct(new BigDecimal("0.01"));
        config.setCreatedAt(LocalDateTime.now());
        merchantFeeConfigRepository.save(config);
    }

    @Test
    void getPaymentLink_shouldReturnFeeBreakdownAndKeepStatusCreated_whenNotExpired() throws Exception {
        PaymentLink link = new PaymentLink();
        link.setPublicId(UUID.randomUUID().toString());
        link.setSlug("get-" + UUID.randomUUID().toString().substring(0, 8));
        link.setMerchant(merchant);
        link.setAmount(new BigDecimal("100.00"));
        link.setCurrency("USD");
        link.setStatus(PaymentLinkStatus.CREATED);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());

        paymentLinkRepository.save(link);

        mockMvc.perform(
                        get("/api/payment-links/{slug}", link.getSlug())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.slug").value(link.getSlug()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.feeBreakdown.baseAmount").value(100.00))
                .andExpect(jsonPath("$.feeBreakdown.processingFee").value(4.00))
                .andExpect(jsonPath("$.feeBreakdown.fxFee").value(1.00))
                .andExpect(jsonPath("$.feeBreakdown.incentiveDiscount").value(0.00))
                .andExpect(jsonPath("$.feeBreakdown.totalFees").value(5.00))
                .andExpect(jsonPath("$.feeBreakdown.finalAmount").value(95.00));
    }

    @Test
    void getPaymentLink_shouldReturnStatusExpired_whenExpiresAtIsInThePast() throws Exception {
        PaymentLink expiredLink = new PaymentLink();
        expiredLink.setPublicId(UUID.randomUUID().toString());
        expiredLink.setSlug("exp-" + UUID.randomUUID().toString().substring(0, 8));
        expiredLink.setMerchant(merchant);
        expiredLink.setAmount(new BigDecimal("100.00"));
        expiredLink.setCurrency("USD");
        expiredLink.setStatus(PaymentLinkStatus.CREATED);
        expiredLink.setCreatedAt(LocalDateTime.now().minusDays(10));
        expiredLink.setUpdatedAt(LocalDateTime.now().minusDays(5));
        expiredLink.setExpiresAt(LocalDateTime.now().minusDays(1));
        paymentLinkRepository.save(expiredLink);

        mockMvc.perform(
                        get("/api/payment-links/{slug}", expiredLink.getSlug())
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.slug").value(expiredLink.getSlug()))
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.feeBreakdown.baseAmount").value(100.00));
    }
}
