package com.trindadeeesx.stocksentry.web.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MinStockRequest {
	@NotNull
	@DecimalMin("0.0")
	private BigDecimal minStock;
}
