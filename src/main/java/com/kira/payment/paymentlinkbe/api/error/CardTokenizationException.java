package com.kira.payment.paymentlinkbe.api.error;

public class CardTokenizationException extends RuntimeException {
  private final String errorCode;

  public CardTokenizationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
