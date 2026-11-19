package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.application.common.NotFoundException;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
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
    private final SecurityUtils securityUtils;

    @CacheEvict(value = {"products", "critical-products"}, allEntries = true)
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
        UUID tenantId = securityUtils.getCurrentTenantId();
        return productRepository.findAllByTenantIdAndActiveTrue(tenantId, pageable)
                .map(this::toResponse);
    }

    public ProductResponse findById(UUID id) {
        return toResponse(getProduct(id));
    }

    @CacheEvict(value = {"products", "critical-products"}, allEntries = true)
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

    @CacheEvict(value = {"products", "critical-products"}, allEntries = true)
    public void delete(UUID id) {
        Product product = getProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @CacheEvict(value = {"products", "critical-products"}, allEntries = true)
    public void hardDelete(UUID id) {
        Product product = getProduct(id);
        productRepository.delete(product);
    }

    @Cacheable(value = "critical-products", key = "#root.target.tenantCacheKey('critical')")
    public List<ProductResponse> findCritical() {
        return productRepository.findCriticalByTenantId(securityUtils.getCurrentTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Cacheable(value = "critical-products", key = "#root.target.tenantCacheKey('out-of-stock')")
    public List<ProductResponse> findOutOfStock() {
        return productRepository.findOutOfStockByTenantId(securityUtils.getCurrentTenantId())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Chave de cache por tenant. Sem o tenant na chave, a primeira empresa a
     * consultar populava o cache e as outras recebiam a lista dela.
     * Público porque é chamado pelo SpEL de @Cacheable (#root.target).
     */
    public String tenantCacheKey(String suffix) {
        return securityUtils.getCurrentTenantId() + ":" + suffix;
    }

    public Product getProduct(UUID id) {
        UUID tenantId = securityUtils.getCurrentTenantId();
        return productRepository.findById(id)
                .filter(p -> p.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    /**
     * Mesmo contrato de {@link #getProduct(UUID)}, mas com lock de escrita na linha.
     * Só faz sentido dentro de uma transação já aberta.
     */
    public Product getProductForUpdate(UUID id) {
        return productRepository.findByIdAndTenantIdForUpdate(id, securityUtils.getCurrentTenantId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
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
