package com.kira.payment.paymentlinkbe.domain.merchant;

public class MerchantNotFoundException extends RuntimeException {
  private final Long merchantId;

  public MerchantNotFoundException(Long merchantId) {
    super("Merchant not found with id: " + merchantId);
    this.merchantId = merchantId;
  }

  public Long getMerchantId() {
    return merchantId;
  }
}
