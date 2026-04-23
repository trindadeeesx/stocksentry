package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.settings.SettingService;
import com.trindadeeesx.stocksentry.web.dto.settings.SettingsRequest;
import com.trindadeeesx.stocksentry.web.dto.settings.SettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

	private final SettingService settingService;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public SettingsResponse get() {
		return toResponse(settingService.getSyncIntervalMs());
	}

	@PatchMapping
	@PreAuthorize("hasRole('ADMIN')")
	public SettingsResponse update(@RequestBody @Valid SettingsRequest request) {
		settingService.updateSyncInterval(request.getSyncIntervalMs());
		return toResponse(request.getSyncIntervalMs());
	}

	private SettingsResponse toResponse(long intervalMs) {
		return SettingsResponse.builder().syncIntervalMs(intervalMs).build();
	}
}
