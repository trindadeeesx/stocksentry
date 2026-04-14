package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsByTenantIdAndSku(UUID tenantId, String sku);
    Page<Product> findAllByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.tenant.id = :tenantId AND p.currentStock <= p.minStock AND p.active = true")
    List<Product> findCriticalByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenant.id = :tenantId AND p.currentStock = 0 AND p.active = true")
    List<Product> findOutOfStockByTenantId(@Param("tenantId") UUID tenantId);
}
