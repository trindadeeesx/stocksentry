package com.trindadeeesx.stocksentry.web.dto.dashboard;

import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import com.trindadeeesx.stocksentry.web.dto.sync.SyncStatusResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
	private SyncStatusResponse syncStatus;
	private int criticalCount;
	private int outOfStockCount;
	private List<ProductResponse> critical;
	private List<ProductResponse> outOfStock;
}
