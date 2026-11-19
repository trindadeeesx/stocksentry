package com.trindadeeesx.stocksentry.application.product;

import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.application.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.Cacheable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceCacheKeyTest {

	/**
	 * Duas empresas consultando a mesma lista não podem cair na mesma chave de cache.
	 */
	@Test
	void shouldBuildDifferentKeys_forDifferentTenants() {
		SecurityUtils securityUtils = mock(SecurityUtils.class);
		ProductService service = new ProductService(mock(ProductRepository.class), securityUtils,
				mock(ApplicationEventPublisher.class), mock(AlertService.class));
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();

		when(securityUtils.getCurrentTenantId()).thenReturn(tenantA);
		String keyA = service.tenantCacheKey("critical");
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantB);
		String keyB = service.tenantCacheKey("critical");

		assertThat(keyA).isEqualTo(tenantA + ":critical");
		assertThat(keyB).isEqualTo(tenantB + ":critical").isNotEqualTo(keyA);
	}

	/**
	 * Trava o contrato: as listas cacheadas usam a chave com tenant. Se alguém
	 * voltar para uma chave fixa, este teste quebra.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"findCritical", "findOutOfStock"})
	void shouldScopeCachedListsByTenant(String methodName) throws NoSuchMethodException {
		Cacheable cacheable = ProductService.class.getMethod(methodName).getAnnotation(Cacheable.class);

		assertThat(cacheable.key()).startsWith("#root.target.tenantCacheKey(");
	}
}
