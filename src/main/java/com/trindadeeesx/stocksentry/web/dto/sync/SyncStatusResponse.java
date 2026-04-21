package com.trindadeeesx.stocksentry.web.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusResponse {
	private LocalDateTime lastSyncAt;
	private int lastCreated;
	private int lastUpdated;
	private int lastCritical;
	private int lastRecovered;
}
