package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.auth.AuthService;
import com.trindadeeesx.stocksentry.application.common.RateLimiter;
import com.trindadeeesx.stocksentry.application.common.TooManyRequestsException;
import com.trindadeeesx.stocksentry.infraestructure.persistence.UserRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

	static final String BODY = """
			{"tenantName":"Loja","tenantSlug":"loja","name":"Ana","email":"ana@x.com","password":"12345678"}
			""";

	AuthService authService = mock(AuthService.class);
	RateLimiter rateLimiter = mock(RateLimiter.class);
	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
						authService, mock(UserRepository.class), mock(SecurityUtils.class), rateLimiter))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	/** O cadastro é público: limita por IP para ninguém criar empresas sem fim. */
	@Test
	void shouldLimitRegisterPerIp() throws Exception {
		when(authService.register(any())).thenReturn(AuthResponse.builder().token("t").build());

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY)
						.with(r -> { r.setRemoteAddr("1.2.3.4"); return r; }))
				.andExpect(status().isCreated());

		verify(rateLimiter).check(eq("register:1.2.3.4"), eq(5), eq(Duration.ofHours(1)), anyString());
	}

	@Test
	void shouldReturn429_whenRegisterLimitIsReached() throws Exception {
		doThrow(new TooManyRequestsException("Muitos cadastros"))
				.when(rateLimiter).check(anyString(), eq(5), any(), anyString());

		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value("Muitos cadastros"));

		verify(authService, never()).register(any());
	}
}
