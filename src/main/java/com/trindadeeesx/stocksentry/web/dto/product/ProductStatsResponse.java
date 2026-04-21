package com.trindadeeesx.stocksentry.web.dto.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStatsResponse {
	private long totalActive;
	private long totalCritical;
	private long totalOutOfStock;
}
