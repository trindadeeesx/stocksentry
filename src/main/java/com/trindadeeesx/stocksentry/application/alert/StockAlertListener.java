package com.trindadeeesx.stocksentry.application.alert;

import com.trindadeeesx.stocksentry.domain.stock.StockBelowMinEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockAlertListener {
	
	private final AlertService alertService;
	
	@EventListener
	public void onStockBelowMin(StockBelowMinEvent event) {
		alertService.processStockAlert(event.getProduct());
	}
}
