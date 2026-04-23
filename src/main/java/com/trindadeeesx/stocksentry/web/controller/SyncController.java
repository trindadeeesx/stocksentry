package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.sync.StockSyncScheduler;
import com.trindadeeesx.stocksentry.web.dto.sync.SyncStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {
	
	private final StockSyncScheduler stockSyncScheduler;
	
	@PostMapping("/now")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, Object>> syncNow() {
		stockSyncScheduler.sync();
		return ResponseEntity.ok(Map.of(
			"message", "Sync triggered successfully",
			"timestamp", LocalDateTime.now().toString()
		));
	}
	
	@GetMapping("/status")
	public SyncStatusResponse status() {
		return stockSyncScheduler.getStatus();
	}
}
