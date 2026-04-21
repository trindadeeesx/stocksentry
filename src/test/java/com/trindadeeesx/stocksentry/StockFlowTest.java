package com.trindadeeesx.stocksentry;

import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.mail.ResendConfig;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class StockFlowTest {
	
	private static final String PRODUCT_ID = "feae5071-dc20-462e-b8b9-d4c1fddcab16";
	@Autowired
	DataSource dataSource;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ProductRepository productRepository;
	
	@MockitoBean
	private ResendConfig resendConfig;
	
	@BeforeEach
	void debug() throws Exception {
		System.out.println(dataSource.getConnection().getMetaData().getURL());
	}
	
	@BeforeEach
	void setup() {
		productRepository.deleteAll();
		
		Product product = new Product();
		product.setId(UUID.fromString(PRODUCT_ID));
		product.setName("Produto Teste");
		product.setCurrentStock(BigDecimal.ZERO);
		product.setMinStock(BigDecimal.TEN);
		
		System.out.println(product.getId() + ", " + product.getName());
		productRepository.saveAndFlush(product);
	}
	
	@Test
	void shouldUpdateStockAndTriggerAlert() throws Exception {
		
		String token = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0cmluZGFkZUBlbWFpbC5jb20iLCJ0ZW5hbnRJZCI6IjdlMDhmMjkyLTQ0OWQtNDI1Ni05YzY0LTdjZGM0NzVhNzc0NSIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc3NjMwNjI1MSwiZXhwIjoxNzc2MzkyNjUxfQ.g2j9ATYCRvSZJW6gZp3c6XxMu4mrEjWa6QYavZ0xWS1nCEh67FF_Ad0KVeqVE6jW3c1FUtqWpaC6smzR4gK9bA";
		
		// ENTRY
		mockMvc.perform(post("/api/v1/products/{id}/movements", PRODUCT_ID)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					    {
					      "type": "ENTRY",
					      "quantity": 15,
					      "reason": "Compra"
					    }
					"""))
			.andExpect(status().isOk());
		
		// EXIT
		mockMvc.perform(post("/api/v1/products/{id}/movements", PRODUCT_ID)
				.header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					    {
					      "type": "EXIT",
					      "quantity": 10,
					      "reason": "Venda"
					    }
					"""))
			.andExpect(status().isOk());
		
		// VALIDAR
		mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID)
				.header("Authorization", token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currentStock").value(5));
	}
}