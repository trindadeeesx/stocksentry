package com.trindadeeesx.stocksentry.application.sync;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.application.settings.SettingService;
import com.trindadeeesx.stocksentry.application.sse.SseEmitterService;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProduct;
import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.web.dto.sync.SyncStatusResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncScheduler {

	private static final BigDecimal DEFAULT_MIN_STOCK = BigDecimal.TEN;

	private final PdvProductRepository pdvProductRepository;
	private final ProductRepository productRepository;
	private final AlertService alertService;
	private final SettingService settingService;
	private final TaskScheduler taskScheduler;
	private final SseEmitterService sseEmitterService;

	@Lazy
	@Autowired
	private StockSyncScheduler self;

	private final AtomicBoolean syncRunning = new AtomicBoolean(false);

	private volatile LocalDateTime lastSyncAt;
	private volatile int lastCreated;
	private volatile int lastUpdated;
	private volatile int lastCritical;
	private volatile int lastRecovered;

	@PostConstruct
	public void start() {
		scheduleNext();
	}

	private void scheduleNext() {
		long interval = settingService.getSyncIntervalMs();
		taskScheduler.schedule(() -> {
			self.sync();
			scheduleNext();
		}, Instant.now().plusMillis(interval));
	}

	@Transactional
	@CacheEvict(value = {"products", "critical-products", "out-of-stock-products"}, allEntries = true)
	public void sync() {
		if (!syncRunning.compareAndSet(false, true)) {
			log.warn("Sync already in progress, skipping.");
			return;
		}
		try {
			doSync();
		} finally {
			syncRunning.set(false);
		}
	}

	public SyncStatusResponse getStatus() {
		return SyncStatusResponse.builder()
			.lastSyncAt(lastSyncAt)
			.lastCreated(lastCreated)
			.lastUpdated(lastUpdated)
			.lastCritical(lastCritical)
			.lastRecovered(lastRecovered)
			.build();
	}

	private void doSync() {
		log.info("Starting stock sync from PDV...");

		List<PdvProduct> pdvProducts = pdvProductRepository.findAllActive();
		int created = 0, updated = 0;
		List<Product> critical = new ArrayList<>();
		List<Product> recovered = new ArrayList<>();

		for (PdvProduct pdv : pdvProducts) {
			Product product = productRepository.findBySku(pdv.getCodigo()).orElse(null);

			if (product == null) {
				product = productRepository.save(Product.builder()
					.sku(pdv.getCodigo())
					.name(pdv.getNome())
					.unit(parseUnit(pdv.getUnidade()))
					.currentStock(pdv.getEstoque())
					.minStock(DEFAULT_MIN_STOCK)
					.active(true)
					.build());
				created++;
				log.debug("Created product: {} ({})", product.getName(), product.getSku());
			} else {
				product.setName(pdv.getNome());
				product.setCurrentStock(pdv.getEstoque());
				product.setActive(true);
				productRepository.save(product);
				updated++;
			}

			if (product.isBelowMinStock()) {
				critical.add(product);
			} else {
				recovered.add(product);
			}
		}

		recovered.forEach(alertService::resetAlert);

		if (!critical.isEmpty()) {
			alertService.processStockAlert(critical);
		}

		lastSyncAt = LocalDateTime.now();
		lastCreated = created;
		lastUpdated = updated;
		lastCritical = critical.size();
		lastRecovered = recovered.size();

		log.info("Sync complete — created: {}, updated: {}, critical: {}, recovered: {}",
			created, updated, critical.size(), recovered.size());
		sseEmitterService.broadcast("sync");
	}

	private UnitType parseUnit(String unit) {
		if (unit == null) return UnitType.UN;
		return switch (unit.toUpperCase().trim()) {
			case "KG" -> UnitType.KG;
			case "L", "LT" -> UnitType.L;
			case "CX" -> UnitType.CX;
			default -> UnitType.UN;
		};
	}
}
