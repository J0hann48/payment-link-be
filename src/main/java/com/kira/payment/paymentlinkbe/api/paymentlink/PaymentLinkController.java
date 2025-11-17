package com.kira.payment.paymentlinkbe.api.paymentlink;

import com.kira.payment.paymentlinkbe.api.payment.UpdatePaymentLinkCommand;
import com.kira.payment.paymentlinkbe.api.payment.UpdatePaymentLinkRequest;
import com.kira.payment.paymentlinkbe.application.paymentlink.CreatePaymentLinkCommand;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkApplicationService;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkInvalidStateException;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkNotFoundException;
import com.kira.payment.paymentlinkbe.application.paymentlink.PaymentLinkView;
import com.kira.payment.paymentlinkbe.application.paymentlink.ProcessPaymentCommand;
import com.kira.payment.paymentlinkbe.application.paymentlink.ProcessPaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Payment Links", description = "Operations for creating and managing payment links")
@RestController
@RequestMapping("/api/payment-links")
@RequiredArgsConstructor
public class PaymentLinkController {

    private final PaymentLinkApplicationService paymentLinkService;

    @Operation(
            summary = "Create payment link",
            description = "Creates a new payment link for a given merchant and optional recipient"
    )
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

    @Operation(
            summary = "Get payment link by slug",
            description = "Returns payment link details and fee preview for checkout"
    )
    @GetMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentLinkResponse getPaymentLink(@PathVariable String slug) {
        PaymentLinkView view = paymentLinkService.getPaymentLink(slug);
        return PaymentLinkResponse.from(view);
    }

    @Operation(
            summary = "Process payment for a payment link",
            description = """
                    Takes a PSP token (tokenized card) and routes the charge via PSP orchestrator.
                    Implements PSP failover (primary + secondary PSP).
                    """
    )
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

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<PaymentLinkResponse> listByMerchant(@RequestParam Long merchantId) {
        List<PaymentLinkView> views = paymentLinkService.listByMerchant(merchantId);
        return views.stream()
                .map(PaymentLinkResponse::from)
                .toList();
    }

    @PutMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentLinkResponse updatePaymentLink(
            @PathVariable String slug,
            @Valid @RequestBody UpdatePaymentLinkRequest request
    ) {
        UpdatePaymentLinkCommand command = new UpdatePaymentLinkCommand(
                request.merchantId(),
                request.recipientId(),
                request.amount(),
                request.currency(),
                request.description(),
                request.expiresAt()
        );

        PaymentLinkView view = paymentLinkService.updatePaymentLink(slug, command);
        return PaymentLinkResponse.from(view);
    }
    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePaymentLink(
            @PathVariable String slug,
            @RequestParam Long merchantId
    ) {
        paymentLinkService.deletePaymentLink(slug, merchantId);
    }

}