package com.kira.payment.paymentlinkbe.application.webhook;

import com.kira.payment.paymentlinkbe.domain.psp.PspCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebhookApplicationService {
    public void handlePspChargeSucceeded(PspCode pspCode, String pspChargeId, String paymentId) {
        log.info(
                "[Webhook] PSP charge SUCCEEDED: pspCode={}, pspChargeId={}, paymentId={}",
                pspCode, pspChargeId, paymentId
        );

        // TODO:
        //  1. Buscar el pago por paymentId
        //  2. Guardar pspCode/pspChargeId si aplica
        //  3. Actualizar estado del pago a SUCCEEDED
        //  4. Disparar eventos de dominio / notificaciones
    }

    public void handlePspChargeFailed(
            PspCode pspCode,
            String pspChargeId,
            String paymentId,
            String failureCode,
            String failureMessage
    ) {
        log.info(
                "[Webhook] PSP charge FAILED: pspCode={}, pspChargeId={}, paymentId={}, failureCode={}, failureMessage={}",
                pspCode, pspChargeId, paymentId, failureCode, failureMessage
        );

        // TODO:
        //  1. Buscar el pago por paymentId
        //  2. Guardar pspCode/pspChargeId + error
        //  3. Actualizar estado del pago a FAILED
        //  4. Disparar eventos de dominio / notificaciones
    }
}
