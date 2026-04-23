package com.trindadeeesx.stocksentry.web.dto.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SettingsRequest {

	@Min(value = 30_000, message = "syncIntervalMs must be at least 30000 (30 seconds)")
	@Max(value = 86_400_000, message = "syncIntervalMs must be at most 86400000 (24 hours)")
	private long syncIntervalMs;
}
