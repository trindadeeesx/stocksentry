package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.web.dto.product.MinStockRequest;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import com.trindadeeesx.stocksentry.web.dto.product.ProductStatsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

	private final ProductService productService;
	
	@GetMapping
	public Page<ProductResponse> findAll(Pageable pageable) {
		return productService.findAll(pageable);
	}
	
	@GetMapping("/{id}")
	public ProductResponse findById(@PathVariable UUID id) {
		return productService.findById(id);
	}
	
	@PatchMapping("/{id}/min-stock")
	@PreAuthorize("hasRole('ADMIN')")
	public ProductResponse updateMinStock(@PathVariable UUID id,
	                                      @RequestBody @Valid MinStockRequest request) {
		return productService.updateMinStock(id, request.getMinStock());
	}
	
	@GetMapping("/critical")
	public List<ProductResponse> critical() {
		return productService.findCritical();
	}
	
	@GetMapping("/out-of-stock")
	public List<ProductResponse> outOfStock() {
		return productService.findOutOfStock();
	}
	
	@GetMapping("/stats")
	public ProductStatsResponse stats() {
		return productService.getStats();
	}
}
