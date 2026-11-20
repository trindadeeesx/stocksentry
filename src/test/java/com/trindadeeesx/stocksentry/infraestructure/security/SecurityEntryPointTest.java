package com.trindadeeesx.stocksentry.infraestructure.security;

import com.trindadeeesx.stocksentry.application.events.SseHub;
import com.trindadeeesx.stocksentry.application.events.SseTicketService;
import com.trindadeeesx.stocksentry.web.controller.EventsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventsController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SecurityEntryPointTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean JwtService jwtService;
	@MockitoBean CustomUserDetailsService userDetailsService;
	@MockitoBean SseTicketService ticketService;
	@MockitoBean SseHub sseHub;
	@MockitoBean SecurityUtils securityUtils;
	@MockitoBean org.springframework.cache.CacheManager cacheManager;

	/**
	 * Sem credencial o Spring Security responde 403 por padrão. O front só
	 * desloga no 401, então JWT ausente ou expirado precisa dar 401.
	 */
	@Test
	void shouldReturn401_whenRequestHasNoCredentials() throws Exception {
		mockMvc.perform(post("/api/v1/events/ticket")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturn401_whenTokenIsInvalid() throws Exception {
		mockMvc.perform(post("/api/v1/events/ticket").header("Authorization", "Bearer lixo"))
				.andExpect(status().isUnauthorized());
	}
}
