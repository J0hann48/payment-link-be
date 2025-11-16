package com.kira.payment.paymentlinkbe.application.paymentlink;

public class PaymentLinkNotFoundException extends RuntimeException {

    private final String slug;

    public PaymentLinkNotFoundException(String slug) {
        super("Payment link not found with slug: " + slug);
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }
}
