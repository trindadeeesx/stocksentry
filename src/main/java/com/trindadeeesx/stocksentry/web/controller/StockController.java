package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.stock.StockService;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementRequest;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/api/v1/products/{productId}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse registerMovement(@PathVariable UUID productId,
                                             @RequestBody @Valid MovementRequest request) {
        return stockService.registerMovement(productId, request);
    }

    @GetMapping("/api/v1/products/{productId}/movements")
    public Page<MovementResponse> findByProduct(@PathVariable UUID productId,
                                                Pageable pageable) {
        return stockService.findByProduct(productId, pageable);
    }

    @GetMapping("/api/v1/movements")
    public Page<MovementResponse> findAll(Pageable pageable) {
        return stockService.findAll(pageable);
    }
}