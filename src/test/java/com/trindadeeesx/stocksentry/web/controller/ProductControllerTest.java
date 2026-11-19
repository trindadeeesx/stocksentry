package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.product.ProductService;
import com.trindadeeesx.stocksentry.web.dto.product.ProductResponse;
import com.trindadeeesx.stocksentry.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

	private ProductService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(ProductService.class);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void shouldReturn400WithField_whenMinStockIsNegative() throws Exception {
		mockMvc.perform(patch("/api/v1/products/{id}/min-stock", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"minStock\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.minStock").exists());

		verify(service, never()).updateMinStock(any(), any());
	}

	@Test
	void shouldReturn400_whenMinStockIsMissing() throws Exception {
		mockMvc.perform(patch("/api/v1/products/{id}/min-stock", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.minStock").exists());
	}

	@Test
	void shouldReturn200_whenMinStockIsValid() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.updateMinStock(id, new BigDecimal("8")))
				.thenReturn(ProductResponse.builder().id(id).minStock(new BigDecimal("8")).build());

		mockMvc.perform(patch("/api/v1/products/{id}/min-stock", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"minStock\":8}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id.toString()));
	}
}
