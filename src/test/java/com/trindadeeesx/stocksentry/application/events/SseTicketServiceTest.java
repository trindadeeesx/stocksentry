package com.trindadeeesx.stocksentry.application.events;

import com.trindadeeesx.stocksentry.application.common.TooManyRequestsException;
import com.trindadeeesx.stocksentry.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseTicketServiceTest {

	MutableClock clock;
	SseTicketService service;
	UUID userId;
	UUID tenantId;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-11-19T12:00:00Z"));
		service = new SseTicketService(clock);
		userId = UUID.randomUUID();
		tenantId = UUID.randomUUID();
	}

	/**
	 * O ticket vai na URL do EventSource e pode parar em log de proxy ou no
	 * histórico do navegador. Por isso só vale uma vez: quem pegar a URL depois
	 * não consegue abrir outro stream com ela.
	 */
	@Test
	void shouldConsumeTicketOnce() {
		String ticket = service.issue(userId, tenantId);

		assertThat(service.consume(ticket))
				.contains(new SseTicketService.TicketOwner(userId, tenantId));
		assertThat(service.consume(ticket)).isEmpty();
	}

	@Test
	void shouldRejectExpiredTicket() {
		String ticket = service.issue(userId, tenantId);

		clock.advance(Duration.ofSeconds(31));

		assertThat(service.consume(ticket)).isEmpty();
	}

	@Test
	void shouldAcceptTicket_justBeforeTtl() {
		String ticket = service.issue(userId, tenantId);

		clock.advance(Duration.ofSeconds(29));

		assertThat(service.consume(ticket)).isPresent();
	}

	@Test
	void shouldRejectUnknownTicket() {
		assertThat(service.consume("nao-existe")).isEmpty();
		assertThat(service.consume(null)).isEmpty();
	}

	@Test
	void shouldIssueDistinctUrlSafeTickets() {
		String a = service.issue(userId, tenantId);
		String b = service.issue(userId, tenantId);

		assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
	}

	/** Um usuário não pode acumular tickets sem usar: o mapa ficaria sem limite. */
	@Test
	void shouldLimitPendingTicketsPerUser() {
		for (int i = 0; i < SseTicketService.MAX_PENDING_PER_USER; i++) {
			service.issue(userId, tenantId);
		}

		assertThatThrownBy(() -> service.issue(userId, tenantId))
				.isInstanceOf(TooManyRequestsException.class);
		assertThat(service.issue(UUID.randomUUID(), tenantId)).isNotBlank();

		clock.advance(Duration.ofSeconds(31));
		assertThat(service.issue(userId, tenantId)).isNotBlank();
	}
}
