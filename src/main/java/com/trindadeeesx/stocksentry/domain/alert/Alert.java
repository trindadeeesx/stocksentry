package com.trindadeeesx.stocksentry.domain.alert;

import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private LocalDateTime triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;
}
