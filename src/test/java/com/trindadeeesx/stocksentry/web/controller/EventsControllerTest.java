package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.common.TooManyRequestsException;
import com.trindadeeesx.stocksentry.application.events.SseHub;
import com.trindadeeesx.stocksentry.application.events.SseTicketService;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventsControllerTest {

	@Mock SseTicketService ticketService;
	@Mock SseHub sseHub;
	@Mock SecurityUtils securityUtils;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new EventsController(ticketService, sseHub, securityUtils))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void shouldIssueTicket_forCurrentUser() throws Exception {
		Tenant tenant = Tenant.builder().id(UUID.randomUUID()).build();
		User user = User.builder().id(UUID.randomUUID()).tenant(tenant).build();
		when(securityUtils.getCurrentUser()).thenReturn(user);
		when(ticketService.issue(user.getId(), tenant.getId())).thenReturn("abc");

		mockMvc.perform(post("/api/v1/events/ticket"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ticket").value("abc"));
	}

	/**
	 * Ticket desconhecido, expirado ou já usado: 401 antes de abrir o stream,
	 * para o front saber que precisa pedir outro ticket.
	 */
	@Test
	void shouldReturn401_withoutOpeningStream_whenTicketIsInvalid() throws Exception {
		when(ticketService.consume("velho")).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/events").param("ticket", "velho")
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isUnauthorized());

		verify(sseHub, never()).subscribe(any());
	}

	@Test
	void shouldOpenStream_forTicketTenant() throws Exception {
		UUID tenantId = UUID.randomUUID();
		when(ticketService.consume("bom"))
				.thenReturn(Optional.of(new SseTicketService.TicketOwner(UUID.randomUUID(), tenantId)));
		when(sseHub.subscribe(tenantId)).thenReturn(new SseEmitter());

		mockMvc.perform(get("/api/v1/events").param("ticket", "bom")
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(request().asyncStarted());

		verify(sseHub).subscribe(tenantId);
	}

	/** Limite de streams do tenant: só o status, a rota é text/event-stream. */
	@Test
	void shouldReturn429_whenTenantHasTooManyStreams() throws Exception {
		UUID tenantId = UUID.randomUUID();
		when(ticketService.consume("bom"))
				.thenReturn(Optional.of(new SseTicketService.TicketOwner(UUID.randomUUID(), tenantId)));
		when(sseHub.subscribe(tenantId)).thenThrow(new TooManyRequestsException("Muitas conexões"));

		mockMvc.perform(get("/api/v1/events").param("ticket", "bom")
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isTooManyRequests());
	}

	/**
	 * Cliente antigo ainda manda ?token= em vez de ?ticket=. Sem ticket é falta
	 * de credencial: 401 direto, sem passar pelo handler de parâmetro ausente
	 * (que tentaria escrever JSON numa rota text/event-stream).
	 */
	@Test
	void shouldReturn401_whenTicketParamIsMissing() throws Exception {
		mockMvc.perform(get("/api/v1/events").param("token", "jwt-antigo")
						.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isUnauthorized());

		verify(sseHub, never()).subscribe(any());
	}
}
