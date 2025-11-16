package com.kira.payment.paymentlinkbe.domain.fx;

import java.math.BigDecimal;
import java.time.Instant;

public record FxQuote(String baseCurrency,
                      String counterCurrency,
                      BigDecimal baseRate,
                      BigDecimal jitterApplied,
                      BigDecimal effectiveRate,
                      Instant quotedAt) {
}
