package com.kira.payment.paymentlinkbe.infraestructure.persistence.payment;

import com.kira.payment.paymentlinkbe.infraestructure.persistence.fee.IncentiveRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncentiveRuleRepository extends JpaRepository<IncentiveRule, Long> {
}
