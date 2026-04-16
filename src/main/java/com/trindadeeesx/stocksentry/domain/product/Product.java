package com.trindadeeesx.stocksentry.domain.product;

import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "sku"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;

    private LocalDateTime lastAlert = null;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public boolean isBelowMinStock() {
        return currentStock.compareTo(minStock) < 0;
    }

    public getLastl() {

    }
}
