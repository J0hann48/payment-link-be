package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentLinkIdAndIdempotencyKey(Long paymentLinkId, String idempotencyKey);

    Optional<Payment> findByPspReference(String pspReference);
}
