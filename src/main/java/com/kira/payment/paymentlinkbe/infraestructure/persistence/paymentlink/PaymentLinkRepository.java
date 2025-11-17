package com.kira.payment.paymentlinkbe.infraestructure.persistence.paymentlink;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<PaymentLink> findBySlugAndMerchantId(String slug, Long merchantId);

    List<PaymentLink> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
