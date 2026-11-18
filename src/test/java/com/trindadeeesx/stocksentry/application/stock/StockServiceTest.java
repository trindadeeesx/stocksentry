package com.trindadeeesx.stocksentry.application.stock;

import com.trindadeeesx.stocksentry.application.alert.AlertService;
import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.domain.stock.MovementType;
import com.trindadeeesx.stocksentry.domain.stock.StockMovement;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.persistence.StockMovementRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementRequest;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

	@Mock StockMovementRepository stockMovementRepository;
	@Mock ProductService productService;
	@Mock SecurityUtils securityUtils;
	@Mock ApplicationEventPublisher eventPublisher;
	@Mock AlertService alertService;

	@InjectMocks StockService stockService;

	Tenant tenant;
	User user;
	Product product;

	@BeforeEach
	void setUp() {
		tenant = Tenant.builder().id(UUID.randomUUID()).name("Loja").slug("loja").active(true).build();
		user = User.builder().id(UUID.randomUUID()).tenant(tenant).name("Operador").build();
		product = Product.builder()
				.id(UUID.randomUUID())
				.tenant(tenant)
				.name("Shampoo 300ml")
				.sku("SH-300")
				.unit(UnitType.values()[0])
				.currentStock(new BigDecimal("10"))
				.minStock(new BigDecimal("5"))
				.active(true)
				.build();
	}

	/**
	 * A movimentação faz leitura-cálculo-escrita do saldo. Sem lock na linha,
	 * duas saídas simultâneas leem o mesmo saldo e uma sobrescreve a outra.
	 * O service precisa buscar o produto pelo caminho com lock, nunca pelo comum.
	 */
	@Test
	void shouldLockProductRow_whenRegisteringMovement() {
		when(productService.getProductForUpdate(product.getId())).thenReturn(product);
		when(securityUtils.getCurrentUser()).thenReturn(user);
		when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

		stockService.registerMovement(product.getId(), request(MovementType.ENTRY, "3"));

		verify(productService).getProductForUpdate(product.getId());
		verify(productService, never()).getProduct(any());
	}

	@Test
	void shouldUpdateStockAndRecordBeforeAndAfter_whenExit() {
		when(productService.getProductForUpdate(product.getId())).thenReturn(product);
		when(securityUtils.getCurrentUser()).thenReturn(user);
		when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

		MovementResponse response = stockService.registerMovement(product.getId(), request(MovementType.EXIT, "4"));

		assertThat(product.getCurrentStock()).isEqualByComparingTo("6");
		assertThat(response.getStockBefore()).isEqualByComparingTo("10");
		assertThat(response.getStockAfter()).isEqualByComparingTo("6");
	}

	@Test
	void shouldRejectExit_whenStockIsInsufficient() {
		when(productService.getProductForUpdate(product.getId())).thenReturn(product);
		when(securityUtils.getCurrentUser()).thenReturn(user);

		assertThatThrownBy(() -> stockService.registerMovement(product.getId(), request(MovementType.EXIT, "11")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Insufficient stock");

		assertThat(product.getCurrentStock()).isEqualByComparingTo("10");
		verify(stockMovementRepository, never()).save(any());
	}

	static MovementRequest request(MovementType type, String quantity) {
		MovementRequest request = new MovementRequest();
		request.setType(type);
		request.setQuantity(new BigDecimal(quantity));
		return request;
	}
}
