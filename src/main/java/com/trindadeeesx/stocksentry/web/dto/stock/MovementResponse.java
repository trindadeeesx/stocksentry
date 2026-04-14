package com.trindadeeesx.stocksentry.web.dto.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trindadeeesx.stocksentry.domain.stock.MovementType;
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
public class MovementResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private MovementType type;
    private BigDecimal quantity;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private String reason;
    private String performedBy;
    private LocalDateTime createdAt;
}
