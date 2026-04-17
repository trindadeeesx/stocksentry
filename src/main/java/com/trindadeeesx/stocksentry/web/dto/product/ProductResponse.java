package com.trindadeeesx.stocksentry.web.dto.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponse {
	private UUID id;
	private String name;
	private String sku;
	private UnitType unit;
	private BigDecimal currentStock;
	private BigDecimal minStock;
	private boolean active;
	private boolean critical;
	private LocalDateTime createdAt;
}
