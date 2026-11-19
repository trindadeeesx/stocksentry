package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsByTenantIdAndSku(UUID tenantId, String sku);
    Page<Product> findAllByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    /** Mesma regra de {@code Product.isBelowMinStock()}: crítico é estritamente abaixo do mínimo. */
    @Query("SELECT p FROM Product p WHERE p.tenant.id = :tenantId AND p.currentStock < p.minStock AND p.active = true")
    List<Product> findCriticalByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenant.id = :tenantId AND p.currentStock = 0 AND p.active = true")
    List<Product> findOutOfStockByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * SELECT ... FOR UPDATE: segura a linha do produto até o fim da transação,
     * serializando movimentações concorrentes do mesmo produto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.tenant.id = :tenantId")
    Optional<Product> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM Product p JOIN FETCH p.tenant WHERE p.id = :id")
    Optional<Product> findWithTenantById(@Param("id") UUID id);

    /**
     * Reserva atômica da janela de alerta: só grava lastAlert se ainda não houve
     * alerta ou se o último é anterior a {@code threshold}. Retorna 1 para quem
     * ganhou a vez e 0 para os demais, então dois eventos simultâneos do mesmo
     * produto não disparam alerta duplicado. Update direto na coluna: não toca
     * em currentStock nem em version.
     */
    @Modifying
    @Query("""
            UPDATE Product p SET p.lastAlert = :now
            WHERE p.id = :id AND (p.lastAlert IS NULL OR p.lastAlert < :threshold)
            """)
    int claimAlertSlot(@Param("id") UUID id,
                       @Param("now") LocalDateTime now,
                       @Param("threshold") LocalDateTime threshold);
}
