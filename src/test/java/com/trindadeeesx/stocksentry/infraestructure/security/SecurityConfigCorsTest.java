package com.trindadeeesx.stocksentry.infraestructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

	/**
	 * O front chama PATCH /products/{id}/min-stock. Em dev o proxy do ng serve
	 * esconde o CORS, mas com front e back em origens diferentes o preflight
	 * barra qualquer método que não esteja na lista.
	 */
	@Test
	void shouldAllowEveryMethodTheApiUses() {
		SecurityConfig config = new SecurityConfig(mock(JwtAuthFilter.class), mock(CustomUserDetailsService.class));
		UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();

		CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("PATCH", "/api/v1/products/1/min-stock"));

		assertThat(cors).isNotNull();
		assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
	}
}
