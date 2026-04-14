package com.trindadeeesx.stocksentry.web.dto.product;

import com.trindadeeesx.stocksentry.domain.product.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String sku;

    @NotNull
    private UnitType unit;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal minStock;
}
