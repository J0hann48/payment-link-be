package com.kira.payment.paymentlinkbe.infraestructure.persistence.psp;

import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import jakarta.persistence.*;

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
@Entity
@Table(name = "psp")
@EqualsAndHashCode(of = "id")
public class Psp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 32)
    private PspCode code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
