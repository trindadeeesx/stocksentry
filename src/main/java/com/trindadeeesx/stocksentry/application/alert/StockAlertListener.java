package com.trindadeeesx.stocksentry.application.alert;

import com.trindadeeesx.stocksentry.domain.stock.StockBelowMinEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StockAlertListener {

    private final AlertService alertService;

    /**
     * AFTER_COMMIT: o alerta só sai se a movimentação foi de fato gravada.
     * Com @EventListener comum, um rollback depois da publicação ainda mandava
     * e-mail/push sobre um estoque que nunca existiu.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockBelowMin(StockBelowMinEvent event) {
        alertService.processStockAlert(event.productId());
    }
}
