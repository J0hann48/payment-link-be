package com.kira.payment.paymentlinkbe.domain.fx;

public interface FxRateProvider {
    FxQuote getQuote(String baseCurrency, String counterCurrency);
}
