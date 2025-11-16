package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.paymentLink pl where pl.slug = :slug")
    Optional<Payment> findByPaymentLinkSlug(@Param("slug") String slug);

    Optional<Payment> findByPspReference(String pspReference);
}
