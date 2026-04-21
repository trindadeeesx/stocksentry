package com.trindadeeesx.stocksentry.application.sync;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProduct;
import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.web.dto.sync.SyncStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncScheduler {
	
	private static final BigDecimal DEFAULT_MIN_STOCK = BigDecimal.TEN;
	
	private final PdvProductRepository pdvProductRepository;
	private final ProductRepository productRepository;
	private final AlertService alertService;
	
	private volatile LocalDateTime lastSyncAt;
	private volatile int lastCreated;
	private volatile int lastUpdated;
	private volatile int lastCritical;
	private volatile int lastRecovered;
	
	public SyncStatusResponse getStatus() {
		return SyncStatusResponse.builder()
			.lastSyncAt(lastSyncAt)
			.lastCreated(lastCreated)
			.lastUpdated(lastUpdated)
			.lastCritical(lastCritical)
			.lastRecovered(lastRecovered)
			.build();
	}
	
	@Scheduled(fixedDelayString = "${scheduler.sync-interval-ms:300000}")
	@Transactional
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public void sync() {
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