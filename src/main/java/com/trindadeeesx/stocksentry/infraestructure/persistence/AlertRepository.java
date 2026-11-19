package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.alert.Alert;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
    Page<Alert> findAllByTenantIdOrderByTriggeredAtDesc(UUID tenantId, Pageable pageable);
    List<Alert> findAllByTenantIdAndTriggeredAtAfter(UUID tenantId, LocalDateTime from);
    boolean existsByProductIdAndStatus(UUID productId, AlertStatus status);
}
