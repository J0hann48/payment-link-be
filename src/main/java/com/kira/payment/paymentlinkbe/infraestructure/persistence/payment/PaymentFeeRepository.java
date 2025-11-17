package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentFeeRepository extends JpaRepository<PaymentFee, Long> {
}
