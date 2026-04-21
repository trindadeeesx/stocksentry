package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.application.sync.StockSyncScheduler;
import com.trindadeeesx.stocksentry.web.dto.dashboard.DashboardSummaryResponse;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
	
	private final ProductService productService;
	private final StockSyncScheduler stockSyncScheduler;
	
	@GetMapping("/summary")
	public DashboardSummaryResponse summary() {
		List<ProductResponse> critical = productService.findCritical();
		List<ProductResponse> outOfStock = productService.findOutOfStock();
		return DashboardSummaryResponse.builder()
			.syncStatus(stockSyncScheduler.getStatus())
			.criticalCount(critical.size())
			.outOfStockCount(outOfStock.size())
			.critical(critical)
			.outOfStock(outOfStock)
			.build();
	}
}
