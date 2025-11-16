package com.kira.payment.paymentlinkbe.infraestructure.persistence.psp;

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
@Table(name = "psp_routing_rule")
public class PspRoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(length = 2)
    private String country;

    @Column(name = "card_brand", length = 16)
    private String cardBrand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_psp_id", nullable = false)
    private Psp primaryPsp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_psp_id")
    private Psp secondaryPsp;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
