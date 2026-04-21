package com.trindadeeesx.stocksentry.infraestructure.persistence;

import com.trindadeeesx.stocksentry.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
	boolean existsBySku(String sku);
	
	Optional<Product> findBySku(String sku);
	
	Page<Product> findAllByActiveTrue(Pageable pageable);
	
	@Query("SELECT p FROM Product p WHERE p.currentStock <= p.minStock AND p.active = true")
	List<Product> findCritical();

	@Query("SELECT p FROM Product p WHERE p.currentStock = 0 AND p.active = true")
	List<Product> findOutOfStock();
	
	long countByActiveTrue();
	
	@Query("SELECT COUNT(p) FROM Product p WHERE p.currentStock <= p.minStock AND p.active = true")
	long countCritical();
	
	@Query("SELECT COUNT(p) FROM Product p WHERE p.currentStock = 0 AND p.active = true")
	long countOutOfStock();
}