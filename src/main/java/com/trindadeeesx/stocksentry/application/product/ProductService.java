package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.product.ProductRequest;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import lombok.RequiredArgsConstructor;
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
    private final SecurityUtils securityUtils;
    private Pageable pageable;

    public ProductResponse create(ProductRequest request) {
        UUID tenantId = securityUtils.getCurrentTenantId();

        if (productRepository.existsByTenantIdAndSku(tenantId, request.getSku())) {
            throw new IllegalArgumentException("SKU already exists for this tenant");
        }

        Product product = productRepository.save(
                Product.builder()
                        .tenant(securityUtils.getCurrentUser().getTenant())
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
        this.pageable = pageable;
        UUID tenantId = securityUtils.getCurrentTenantId();
        return productRepository.findAllByTenantIdAndActiveTrue(tenantId, pageable)
                .map(this::toResponse);
    }

    public ProductResponse findById(UUID id) {
        return toResponse(getProduct(id));
    }

    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = getProduct(id);

        UUID tenantId = securityUtils.getCurrentTenantId();
        if (!request.getSku().equals(product.getSku()) &&
                productRepository.existsByTenantIdAndSku(tenantId, request.getSku())) {
            throw new IllegalArgumentException("SKU already exists for this tenant");
        }

        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setUnit(request.getUnit());
        product.setMinStock(request.getMinStock());

        return toResponse(productRepository.save(product));
    }

    public void delete(UUID id) {
        Product product = getProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    public List<ProductResponse> findCritical() {
        return productRepository.findCriticalByTenantId(securityUtils.getCurrentTenantId())
                .stream().map(this::toResponse).toList();
    }

    public List<ProductResponse> findOutOfStock() {
        return productRepository.findOutOfStockByTenantId(securityUtils.getCurrentTenantId())
                .stream().map(this::toResponse).toList();
    }

    public Product getProduct(UUID id) {
        UUID tenantId = securityUtils.getCurrentTenantId();
        return productRepository.findById(id)
                .filter(p -> p.getTenant().getId().equals(tenantId))
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
