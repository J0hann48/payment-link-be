package com.kira.payment.paymentlinkbe.infraestructure.persistence.fee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantFeeConfigRepository extends JpaRepository<MerchantFeeConfig, Long> {
    Optional<MerchantFeeConfig> findByMerchantId(Long merchantId);
}
