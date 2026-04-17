package com.trindadeeesx.stocksentry.web.dto.alert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertResponse {
	private UUID id;
	private String productName;
	private AlertType type;
	private String destination;
	private AlertStatus status;
	private LocalDateTime triggeredAt;
}
