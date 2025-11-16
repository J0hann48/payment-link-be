package com.kira.payment.paymentlinkbe.infraestructure.persistence.fee;

import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "merchant_fee_config")
public class MerchantFeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(length = 128)
    private String name;

    @Column(length = 3)
    private String currency;

    @Column(name = "fixed_fee", precision = 18, scale = 2)
    private BigDecimal fixedFee;

    @Column(name = "percentage_fee", precision = 5, scale = 4)
    private BigDecimal percentageFee;

    @Column(name = "fx_markup_pct", precision = 5, scale = 4)
    private BigDecimal fxMarkupPct;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
