package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigRequest;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigResponse;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

	private final AlertService alertService;

	@PostMapping("/config")
	@ResponseStatus(HttpStatus.CREATED)
	public AlertConfigResponse createConfig(@RequestBody @Valid AlertConfigRequest request) {
		return alertService.createConfig(request);
	}
	
	@GetMapping("/config")
	public List<AlertConfigResponse> findConfigs() {
		return alertService.findConfigs();
	}
	
	@DeleteMapping("/config/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteConfig(@PathVariable UUID id) {
		alertService.deleteConfig(id);
	}
	
	@GetMapping("/history")
	public Page<AlertResponse> history(Pageable pageable) {
		return alertService.findAlertHistory(pageable);
	}
	
	@PostMapping("/report")
	public ResponseEntity<Map<String, Object>> triggerReport(
		@RequestParam(defaultValue = "7") int days) {
		alertService.sendReport(days);
		return ResponseEntity.ok(Map.of(
			"message", "Report triggered for last " + days + " days",
			"timestamp", LocalDateTime.now().toString()
		));
	}
}
