package com.trindadeeesx.stocksentry.application.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService {

	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		return emitter;
	}

	public void broadcast(String eventName) {
		List<SseEmitter> dead = new ArrayList<>();
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name(eventName).data(""));
			} catch (Exception e) {
				dead.add(emitter);
			}
		}
		emitters.removeAll(dead);
	}

	@Scheduled(fixedDelay = 25_000)
	public void heartbeat() {
		broadcast("heartbeat");
	}
}
