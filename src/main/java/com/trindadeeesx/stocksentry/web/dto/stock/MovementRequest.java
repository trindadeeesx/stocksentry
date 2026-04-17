package com.trindadeeesx.stocksentry.web.dto.stock;

import com.trindadeeesx.stocksentry.domain.stock.MovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MovementRequest {
	@NotNull
	private MovementType type;
	
	@NotNull
	@DecimalMin("0.001")
	private BigDecimal quantity;
	
	private String reason;
}
