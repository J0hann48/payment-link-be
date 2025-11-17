package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIncentiveRepository extends JpaRepository<PaymentIncentive, Long> {
}
