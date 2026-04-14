package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertConfigRepository extends JpaRepository<AlertConfig, UUID> {
    List<AlertConfig> findAllByTenantIdAndActiveTrue(UUID tenantId);
}
