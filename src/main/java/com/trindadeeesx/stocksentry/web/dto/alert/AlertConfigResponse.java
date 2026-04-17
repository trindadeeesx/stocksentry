package com.trindadeeesx.stocksentry.web.dto.alert;

import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AlertConfigResponse {
	private UUID id;
	private AlertType type;
	private String destination;
	private boolean active;
}