package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.application.common.ConflictException;
import com.trindadeeesx.stocksentry.application.common.NotFoundException;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.domain.stock.StockBelowMinEvent;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.product.ProductRequest;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock ProductRepository productRepository;
	@Mock SecurityUtils securityUtils;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock AlertService alertService;

	@InjectMocks ProductService productService;

	UUID tenantId;
	Product product;

	@BeforeEach
	void setUp() {
		tenantId = UUID.randomUUID();
		product = Product.builder()
				.id(UUID.randomUUID())
				.name("Shampoo")
				.sku("SKU-1")
				.unit(UnitType.UN)
				.currentStock(new BigDecimal("5"))
				.minStock(new BigDecimal("3"))
				.active(true)
				.build();
	}

	private void givenProductFound() {
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(productRepository.findByIdAndTenantIdForUpdate(product.getId(), tenantId))
				.thenReturn(Optional.of(product));
		when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void shouldPublishEvent_whenNewMinMakesProductCritical() {
		givenProductFound();

		ProductResponse response = productService.updateMinStock(product.getId(), new BigDecimal("8"));

		verify(eventPublisher).publishEvent(new StockBelowMinEvent(product.getId()));
		verify(alertService, never()).resetAlert(any());
		assertThat(response.getMinStock()).isEqualByComparingTo("8");
		assertThat(response.isCritical()).isTrue();
	}

	@Test
	void shouldResetAlert_whenNewMinTakesProductOutOfCritical() {
		product.setMinStock(new BigDecimal("8"));
		givenProductFound();

		ProductResponse response = productService.updateMinStock(product.getId(), new BigDecimal("3"));

		verify(alertService).resetAlert(product);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
		assertThat(response.isCritical()).isFalse();
	}

	@Test
	void shouldNotPublish_whenStateDoesNotChange() {
		givenProductFound();

		productService.updateMinStock(product.getId(), new BigDecimal("4"));

		verify(productRepository).save(product);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
		verify(alertService, never()).resetAlert(any());
	}

	@Test
	void shouldThrowNotFound_whenProductBelongsToAnotherTenant() {
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(productRepository.findByIdAndTenantIdForUpdate(product.getId(), tenantId))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> productService.updateMinStock(product.getId(), new BigDecimal("8")))
				.isInstanceOf(NotFoundException.class);

		verify(productRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	/** SKU repetido no tenant é conflito com o que já existe: 409, não 400. */
	@Test
	void shouldThrowConflict_whenCreatingWithDuplicatedSku() {
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(productRepository.existsByTenantIdAndSku(tenantId, "SKU-1")).thenReturn(true);

		assertThatThrownBy(() -> productService.create(request("SKU-1")))
				.isInstanceOf(ConflictException.class)
				.hasMessage("SKU already exists for this tenant");

		verify(productRepository, never()).save(any());
	}

	@Test
	void shouldThrowConflict_whenUpdatingToDuplicatedSku() {
		product.setTenant(Tenant.builder().id(tenantId).build());
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
		when(productRepository.existsByTenantIdAndSku(tenantId, "SKU-2")).thenReturn(true);

		assertThatThrownBy(() -> productService.update(product.getId(), request("SKU-2")))
				.isInstanceOf(ConflictException.class)
				.hasMessage("SKU already exists for this tenant");

		verify(productRepository, never()).save(any());
	}

	private ProductRequest request(String sku) {
		ProductRequest request = new ProductRequest();
		request.setName("Shampoo");
		request.setSku(sku);
		request.setUnit(UnitType.UN);
		request.setMinStock(new BigDecimal("3"));
		return request;
	}
}
