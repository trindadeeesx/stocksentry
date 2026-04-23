package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import com.trindadeeesx.stocksentry.web.dto.product.ProductStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;

	public Page<ProductResponse> findAll(Pageable pageable) {
		return productRepository.findAllByActiveTrue(pageable).map(this::toResponse);
	}
	
	public ProductResponse findById(UUID id) {
		return toResponse(getProduct(id));
	}
	
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public ProductResponse updateMinStock(UUID id, BigDecimal minStock) {
		Product product = getProduct(id);
		product.setMinStock(minStock);
		return toResponse(productRepository.save(product));
	}
	
	@Cacheable("critical-products")
	public List<ProductResponse> findCritical() {
		return productRepository.findCritical().stream().map(this::toResponse).toList();
	}
	
	@Cacheable("out-of-stock-products")
	public List<ProductResponse> findOutOfStock() {
		return productRepository.findOutOfStock().stream().map(this::toResponse).toList();
	}
	
	public ProductStatsResponse getStats() {
		return ProductStatsResponse.builder()
			.totalActive(productRepository.countByActiveTrue())
			.totalCritical(productRepository.countCritical())
			.totalOutOfStock(productRepository.countOutOfStock())
			.build();
	}

	public Product getProduct(UUID id) {
		return productRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("Product not found"));
	}
	
	private ProductResponse toResponse(Product product) {
		return ProductResponse.builder()
			.id(product.getId())
			.name(product.getName())
			.sku(product.getSku())
			.unit(product.getUnit())
			.currentStock(product.getCurrentStock())
			.minStock(product.getMinStock())
			.active(product.isActive())
			.critical(product.isBelowMinStock())
			.createdAt(product.getCreatedAt())
			.build();
	}
}
