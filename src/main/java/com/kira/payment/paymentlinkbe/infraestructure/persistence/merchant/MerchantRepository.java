package com.kira.payment.paymentlinkbe.infraestructure.persistence.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
}
