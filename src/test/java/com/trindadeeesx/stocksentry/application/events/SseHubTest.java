package com.trindadeeesx.stocksentry.application.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseHubTest {

	SseHub hub;
	UUID tenantA;
	UUID tenantB;

	@BeforeEach
	void setUp() {
		hub = new SseHub();
		tenantA = UUID.randomUUID();
		tenantB = UUID.randomUUID();
	}

	@Test
	void shouldDeliverOnlyToSameTenant() {
		RecordingEmitter deA = new RecordingEmitter();
		RecordingEmitter deB = new RecordingEmitter();
		hub.register(tenantA, deA);
		hub.register(tenantB, deB);

		hub.publish(tenantA, "alert");

		assertThat(deA.sent).isEqualTo(1);
		assertThat(deB.sent).isZero();
	}

	/**
	 * Uma aba fechada sem aviso deixa um emitter morto na lista. O envio para ele
	 * falha; isso não pode impedir as outras abas do tenant de receberem o evento,
	 * e o morto sai da lista para não ser tentado de novo.
	 */
	@Test
	void shouldDropBrokenEmitter_andKeepDeliveringToOthers() {
		BrokenEmitter quebrado = new BrokenEmitter();
		RecordingEmitter saudavel = new RecordingEmitter();
		hub.register(tenantA, quebrado);
		hub.register(tenantA, saudavel);

		hub.publish(tenantA, "alert");
		hub.publish(tenantA, "config");

		assertThat(saudavel.sent).isEqualTo(2);
		assertThat(quebrado.attempts).isEqualTo(1);
	}

	/**
	 * Quando o único emitter do tenant sai, a lista do tenant some do mapa. Uma
	 * aba que conecta depois tem que entrar numa lista viva e receber eventos.
	 */
	@Test
	void shouldDeliverToNewSubscriber_afterTenantLostItsOnlyEmitter() {
		BrokenEmitter quebrado = new BrokenEmitter();
		hub.register(tenantA, quebrado);
		hub.publish(tenantA, "alert");

		RecordingEmitter novo = new RecordingEmitter();
		hub.register(tenantA, novo);
		hub.publish(tenantA, "config");

		assertThat(novo.sent).isEqualTo(1);
		assertThat(quebrado.attempts).isEqualTo(1);
	}

	@Test
	void shouldSendHeartbeatToEveryTenant() {
		RecordingEmitter deA = new RecordingEmitter();
		RecordingEmitter deB = new RecordingEmitter();
		hub.register(tenantA, deA);
		hub.register(tenantB, deB);

		hub.heartbeat();

		assertThat(deA.sent).isEqualTo(1);
		assertThat(deB.sent).isEqualTo(1);
	}

	@Test
	void shouldIgnorePublish_whenTenantHasNoSubscribers() {
		hub.publish(UUID.randomUUID(), "alert");
	}

	static class RecordingEmitter extends SseEmitter {
		int sent;

		@Override
		public void send(SseEventBuilder builder) {
			sent++;
		}
	}

	static class BrokenEmitter extends SseEmitter {
		int attempts;

		@Override
		public void send(SseEventBuilder builder) throws IOException {
			attempts++;
			throw new IOException("Broken pipe");
		}
	}
}
