package com.trindadeeesx.stocksentry.web.dto.settings;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettingsResponse {
	private long syncIntervalMs;
}
