package com.kira.payment.paymentlinkbe.infraestructure.persistence.fx;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateSnapshotRepository extends JpaRepository<FxRateSnapshot, Long> {
}
