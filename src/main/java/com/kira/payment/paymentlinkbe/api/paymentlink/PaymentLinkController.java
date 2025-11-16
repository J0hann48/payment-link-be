package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.application.paymentlink.CreatePaymentLinkCommand;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkApplicationService;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkInvalidStateException;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkNotFoundException;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkView;
import com.kira.payment.paymentlinkbe.application.paymentlink.ProcessPaymentCommand;
import com.kira.payment.paymentlinkbe.application.paymentlink.ProcessPaymentResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment-links")
@RequiredArgsConstructor
public class PaymentLinkController {

    private final PaymentLinkApplicationService paymentLinkService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse createPaymentLink(
            @Valid @RequestBody CreatePaymentLinkRequest request
    ) {
        CreatePaymentLinkCommand command = new CreatePaymentLinkCommand(
                request.merchantId(),
                request.recipientId(),
                request.amount(),
                request.currency(),
                request.description(),
                request.expiresAt()
        );

        PaymentLinkView view = paymentLinkService.createPaymentLink(command);
        return PaymentLinkResponse.from(view);
    }

    @GetMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentLinkResponse getPaymentLink(@PathVariable String slug) {
        PaymentLinkView view = paymentLinkService.getPaymentLink(slug);
        return PaymentLinkResponse.from(view);
    }

    @PostMapping("/{slug}/pay")
    public ProcessPaymentResponse processPayment(
            @PathVariable String slug,
            @Valid @RequestBody ProcessPaymentRequest request
    ) {
        ProcessPaymentResult result = paymentLinkService.processPayment(
                slug,
                new ProcessPaymentCommand(request.pspToken(), request.pspHint())
        );
        return ProcessPaymentResponse.from(result);
    }

}