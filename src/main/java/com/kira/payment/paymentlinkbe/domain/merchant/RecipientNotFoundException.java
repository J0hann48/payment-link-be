package com.kira.payment.paymentlinkbe.domain.merchant;

public class RecipientNotFoundException extends RuntimeException {
    private final Long recipientId;

    public RecipientNotFoundException(Long recipientId) {
        super("Recipient not found with id: " + recipientId);
        this.recipientId = recipientId;
    }

    public Long getRecipientId() {
        return recipientId;
    }
}
