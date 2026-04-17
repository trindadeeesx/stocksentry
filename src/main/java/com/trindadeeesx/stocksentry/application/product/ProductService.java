package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.web.dto.product.ProductRequest;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
	
	private final ProductRepository productRepository;
	
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public ProductResponse create(ProductRequest request) {
		if (productRepository.existsBySku(request.getSku())) {
			throw new IllegalArgumentException("SKU already exists");
		}
		
		Product product = productRepository.save(
			Product.builder()
				.name(request.getName())
				.sku(request.getSku())
				.unit(request.getUnit())
				.currentStock(BigDecimal.ZERO)
				.minStock(request.getMinStock())
				.active(true)
				.build()
		);
		
		return toResponse(product);
	}
	
	public Page<ProductResponse> findAll(Pageable pageable) {
		return productRepository.findAllByActiveTrue(pageable).map(this::toResponse);
	}
	
	public ProductResponse findById(UUID id) {
		return toResponse(getProduct(id));
	}
	
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public ProductResponse update(UUID id, ProductRequest request) {
		Product product = getProduct(id);
		
		if (!request.getSku().equals(product.getSku()) && productRepository.existsBySku(request.getSku())) {
			throw new IllegalArgumentException("SKU already exists");
		}
		
		product.setName(request.getName());
		product.setSku(request.getSku());
		product.setUnit(request.getUnit());
		product.setMinStock(request.getMinStock());
		
		return toResponse(productRepository.save(product));
	}
	
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public void delete(UUID id) {
		Product product = getProduct(id);
		product.setActive(false);
		productRepository.save(product);
	}
	
	@CacheEvict(value = {"products", "critical-products"}, allEntries = true)
	public void hardDelete(UUID id) {
		productRepository.delete(getProduct(id));
	}
	
	@Cacheable(value = "critical-products", key = "'critical'")
	public List<ProductResponse> findCritical() {
		return productRepository.findCritical().stream().map(this::toResponse).toList();
	}
	
	@Cacheable(value = "critical-products", key = "'out-of-stock'")
	public List<ProductResponse> findOutOfStock() {
		return productRepository.findOutOfStock().stream().map(this::toResponse).toList();
	}
	
	public Product getProduct(UUID id) {
		return productRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Product not found"));
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