package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.stock.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    Page<StockMovement> findAllByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
    Page<StockMovement> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT m FROM StockMovement m WHERE m.tenant.id = :tenantId AND m.createdAt BETWEEN :from AND :to")
    List<StockMovement> findByTenantIdAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
