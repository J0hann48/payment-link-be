package com.kira.payment.paymentlinkbe.infraestructure.persistence.psp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PspRepository extends JpaRepository<Psp, Long> {
    Optional<Psp> findByCode(String code);
}
