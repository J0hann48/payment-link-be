package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import com.kira.payment.paymentlinkbe.domain.payment.PaymentStatus;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Merchant;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant.Recipient;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink.PaymentLink;
import com.kira.payment.paymentlinkbe.infraestructure.persistence.psp.Psp;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_link_id", nullable = false)
    private PaymentLink paymentLink;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id")
    private Psp psp;

    @Builder.Default
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentFee> fees = new ArrayList<>();

    @Column(name = "psp_reference", length = 128)
    private String pspReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee_total", precision = 18, scale = 2)
    private BigDecimal feeTotal;

    @Column(name = "net_amount", precision = 18, scale = 2)
    private BigDecimal netAmount;

    @Column(length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
