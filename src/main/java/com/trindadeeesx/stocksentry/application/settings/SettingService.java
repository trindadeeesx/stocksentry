package com.trindadeeesx.stocksentry.application.settings;

import com.trindadeeesx.stocksentry.domain.settings.AppSettings;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AppSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettingService {

	private static final long MIN_INTERVAL_MS = 30_000L;
	private static final long MAX_INTERVAL_MS = 86_400_000L;

	private final AppSettingsRepository repository;

	@Transactional(readOnly = true)
	public AppSettings getSettings() {
		return repository.findById((short) 1).orElseGet(() -> {
			AppSettings defaults = new AppSettings();
			return repository.save(defaults);
		});
	}

	public long getSyncIntervalMs() {
		return getSettings().getSyncIntervalMs();
	}

	@Transactional
	public AppSettings updateSyncInterval(long intervalMs) {
		if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
			throw new IllegalArgumentException(
				"syncIntervalMs must be between " + MIN_INTERVAL_MS + " and " + MAX_INTERVAL_MS
			);
		}
		AppSettings settings = getSettings();
		settings.setSyncIntervalMs(intervalMs);
		return repository.save(settings);
	}
}
