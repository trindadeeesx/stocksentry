package com.trindadeeesx.stocksentry.domain.product;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
	
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	
	@Column(nullable = false)
	private String name;
	
	@Column(nullable = false, unique = true)
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
}