package com.trindadeeesx.stocksentry.web.handler;

import com.trindadeeesx.stocksentry.application.common.BusinessRuleException;
import com.trindadeeesx.stocksentry.application.common.ConflictException;
import com.trindadeeesx.stocksentry.application.common.NotFoundException;
import com.trindadeeesx.stocksentry.web.dto.stock.MovementRequest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyPath;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@AfterEach
	void detachLogs() {
		if (appender != null) {
			handlerLogger().detachAppender(appender);
		}
	}

	@Test
	void shouldReturn404_whenNotFoundExceptionIsThrown() throws Exception {
		mockMvc.perform(get("/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Product not found"));
	}

	@Test
	void shouldReturn404_whenPathDoesNotExist() throws Exception {
		mockMvc.perform(get("/nao-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not found"));
	}

	@Test
	void shouldReturn400_whenBodyIsUnreadable() throws Exception {
		mockMvc.perform(post("/movement")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"IN\",\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Corpo da requisição inválido"));
	}

	@Test
	void shouldReturn400_whenParameterHasWrongType() throws Exception {
		mockMvc.perform(get("/typed").param("id", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Parâmetro inválido: id"));
	}

	@Test
	void shouldReturn400_whenRequiredParameterIsMissing() throws Exception {
		mockMvc.perform(get("/typed"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Parâmetro inválido: id"));
	}

	@Test
	void shouldReturn403_whenSpringSecurityDeniesAccess() throws Exception {
		mockMvc.perform(get("/denied"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Access denied"));
	}

	@Test
	void shouldReturn409_whenConflictExceptionIsThrown() throws Exception {
		mockMvc.perform(get("/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Conflito"));
	}

	/**
	 * Dois cadastros simultâneos com o mesmo e-mail/slug passam pelo exists e o
	 * segundo esbarra na constraint única do banco. Isso é conflito, não 500.
	 */
	@Test
	void shouldReturn409_whenDataIntegrityIsViolated() throws Exception {
		mockMvc.perform(get("/integrity"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Conflict"));
	}

	/** ?sort=foo em endpoint paginado é erro do cliente, não do servidor. */
	@Test
	void shouldReturn400_whenSortPropertyIsUnknown() throws Exception {
		mockMvc.perform(get("/bad-sort"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Parâmetro inválido: sort"));
	}

	@Test
	void shouldReturn422_whenBusinessRuleExceptionIsThrown() throws Exception {
		mockMvc.perform(get("/rule"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.error").value("Regra violada"));
	}

	/**
	 * ResponseStatusException já diz o status que quer (ex.: 401 do ticket SSE
	 * inválido). O handler genérico de Exception não pode engolir isso como 500.
	 */
	@Test
	void shouldKeepStatus_whenResponseStatusExceptionIsThrown() throws Exception {
		mockMvc.perform(get("/unauthorized"))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * Timeout e desconexão de SSE são rotina, não erro: nada de log ERROR com
	 * stack, e nada de tentar escrever um Map numa resposta text/event-stream.
	 */
	@Test
	void shouldHandleAsyncTimeoutQuietly() throws Exception {
		ListAppender<ILoggingEvent> logs = attachLogs();

		mockMvc.perform(get("/async-timeout"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().string(""));

		assertThat(logs.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
	}

	@Test
	void shouldHandleClientDisconnectQuietly() throws Exception {
		ListAppender<ILoggingEvent> logs = attachLogs();

		mockMvc.perform(get("/async-gone"))
				.andExpect(content().string(""));

		assertThat(logs.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
	}

	@Test
	void shouldReturn500WithoutLeakingDetails_whenUnexpectedErrorOccurs() throws Exception {
		mockMvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("Internal server error"));
	}

	private ListAppender<ILoggingEvent> attachLogs() {
		appender = new ListAppender<>();
		appender.start();
		handlerLogger().addAppender(appender);
		return appender;
	}

	private Logger handlerLogger() {
		return (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
	}

	static class Sample {
		String name;
	}

	@RestController
	static class FakeController {

		@GetMapping("/async-timeout")
		void throwAsyncTimeout() {
			throw new AsyncRequestTimeoutException();
		}

		@GetMapping("/async-gone")
		void throwAsyncGone() throws Exception {
			throw new AsyncRequestNotUsableException("Broken pipe");
		}

		@GetMapping("/not-found")
		void throwNotFound() {
			throw new NotFoundException("Product not found");
		}

		@GetMapping("/conflict")
		void throwConflict() {
			throw new ConflictException("Conflito");
		}

		@GetMapping("/integrity")
		void throwIntegrity() {
			throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
		}

		@GetMapping("/bad-sort")
		void throwBadSort() {
			PropertyPath.from("foo", Sample.class);
		}

		@GetMapping("/rule")
		void throwRule() {
			throw new BusinessRuleException("Regra violada");
		}

		@GetMapping("/denied")
		void throwDenied() {
			throw new AccessDeniedException("negado");
		}

		@GetMapping("/unauthorized")
		void throwUnauthorized() {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}

		@GetMapping("/boom")
		void throwBoom() {
			throw new IllegalStateException("segredo interno");
		}

		@GetMapping("/typed")
		String typed(@RequestParam UUID id) {
			return id.toString();
		}

		@PostMapping("/movement")
		String movement(@RequestBody @Valid MovementRequest request) {
			return "ok";
		}
	}
}
