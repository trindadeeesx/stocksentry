package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProduct;
import com.trindadeeesx.stocksentry.infraestructure.pdv.PdvProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/debug")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DebugController {

	private final PdvProductRepository pdvProductRepository;

	@GetMapping("/pdv/products")
	public List<PdvProduct> listPdvProducts() {
		return pdvProductRepository.findAll();
	}

	@PatchMapping("/pdv/products/{id}/stock")
	public ResponseEntity<Map<String, Object>> updateStock(
		@PathVariable Long id,
		@RequestBody Map<String, BigDecimal> body
	) {
		BigDecimal estoque = body.get("estoque");
		if (estoque == null || estoque.compareTo(BigDecimal.ZERO) < 0) {
			return ResponseEntity.badRequest().body(Map.of("error", "estoque deve ser >= 0"));
		}
		int rows = pdvProductRepository.updateEstoque(id, estoque);
		if (rows == 0) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(Map.of("id", id, "estoque", estoque));
	}
}
