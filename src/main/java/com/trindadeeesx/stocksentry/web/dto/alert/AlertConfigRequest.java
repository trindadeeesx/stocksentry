package com.trindadeeesx.stocksentry.web.dto.alert;

import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlertConfigRequest {
	@NotNull
	private AlertType type;

	@Size(max = 150)
	private String destination;
}
