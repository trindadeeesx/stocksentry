package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.web.dto.product.ProductRequest;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
	
	private final ProductService productService;
	
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@RequestBody @Valid ProductRequest request) {
		return productService.create(request);
	}
	
	@GetMapping
	public Page<ProductResponse> findAll(Pageable pageable) {
		return productService.findAll(pageable);
	}
	
	@GetMapping("/{id}")
	public ProductResponse findById(@PathVariable UUID id) {
		return productService.findById(id);
	}
	
	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable UUID id,
	                              @RequestBody @Valid ProductRequest request) {
		return productService.update(id, request);
	}
	
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		productService.delete(id);
	}
	
	@DeleteMapping("/{id}/hard")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void hardDelete(@PathVariable UUID id) {
		productService.hardDelete(id);
	}
	
	@GetMapping("/critical")
	public List<ProductResponse> critical() {
		return productService.findCritical();
	}
	
	@GetMapping("/out-of-stock")
	public List<ProductResponse> outOfStock() {
		return productService.findOutOfStock();
	}
}