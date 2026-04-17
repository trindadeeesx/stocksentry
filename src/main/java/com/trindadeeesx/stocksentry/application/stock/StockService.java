package com.trindadeeesx.stocksentry.application.stock;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.stock.StockBelowMinEvent;
import com.trindadeeesx.stocksentry.domain.stock.StockMovement;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.persistence.StockMovementRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementRequest;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {
	private final StockMovementRepository stockMovementRepository;
	private final ProductService productService;
	private final SecurityUtils securityUtils;
	private final ApplicationEventPublisher eventPublisher;
	private final AlertService alertService;
	
	@CacheEvict(value = {"products", "critical-products", "stock-summary"}, allEntries = true)
	@Transactional
	public MovementResponse registerMovement(UUID productId, MovementRequest request) {
		Product product = productService.getProduct(productId);
		User user = securityUtils.getCurrentUser();
		
		boolean wasBelowMin = product.isBelowMinStock();
		
		BigDecimal stockBefore = product.getCurrentStock();
		BigDecimal stockAfter = calculateNewStock(product, request);
		product.setCurrentStock(stockAfter);
		
		StockMovement movement = stockMovementRepository.save(
			StockMovement.builder()
				.product(product)
				.tenant(user.getTenant())
				.user(user)
				.type(request.getType())
				.quantity(request.getQuantity())
				.stockBefore(stockBefore)
				.stockAfter(stockAfter)
				.reason(request.getReason())
				.build()
		);
		
		if (product.isBelowMinStock()) {
			eventPublisher.publishEvent(new StockBelowMinEvent(this, product));
		} else if (wasBelowMin) {
			alertService.resetAlert(product);
		}
		
		return toResponse(movement);
	}
	
	public Page<MovementResponse> findByProduct(UUID productId, Pageable pageable) {
		productService.getProduct(productId);
		return stockMovementRepository
			.findAllByProductIdOrderByCreatedAtDesc(productId, pageable)
			.map(this::toResponse);
	}
	
	public Page<MovementResponse> findAll(Pageable pageable) {
		UUID tenantId = securityUtils.getCurrentTenantId();
		return stockMovementRepository
			.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
			.map(this::toResponse);
	}
	
	private BigDecimal calculateNewStock(Product product, MovementRequest request) {
		return switch (request.getType()) {
			case ENTRY -> product.getCurrentStock().add(request.getQuantity());
			case EXIT -> {
				BigDecimal result = product.getCurrentStock().subtract(request.getQuantity());
				if (result.compareTo(BigDecimal.ZERO) < 0) {
					throw new IllegalArgumentException("Insufficient stock");
				}
				yield result;
			}
			case ADJUSTMENT -> request.getQuantity();
		};
	}
	
	private MovementResponse toResponse(StockMovement m) {
		return MovementResponse.builder()
			.id(m.getId())
			.productId(m.getProduct().getId())
			.productName(m.getProduct().getName())
			.type(m.getType())
			.quantity(m.getQuantity())
			.stockBefore(m.getStockBefore())
			.stockAfter(m.getStockAfter())
			.reason(m.getReason())
			.performedBy(m.getUser().getName())
			.createdAt(m.getCreatedAt())
			.build();
	}
	
}
