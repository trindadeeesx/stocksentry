package com.trindadeeesx.stocksentry.application.events;

import com.trindadeeesx.stocksentry.application.common.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro em memória das conexões SSE abertas, por tenant. Os eventos só
 * avisam o front de que algo mudou ("alert", "config"); o front busca os dados
 * pela API normal, já com o isolamento por tenant.
 */
@Slf4j
@Component
public class SseHub {

    static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    /** Streams abertos por tenant; abas esquecidas não acumulam sem fim. */
    static final int MAX_STREAMS_PER_TENANT = 50;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        if (subscriberCount(tenantId) >= MAX_STREAMS_PER_TENANT) {
            throw new TooManyRequestsException("Muitas conexões abertas para esta empresa");
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        register(tenantId, emitter);
        return emitter;
    }

    void register(UUID tenantId, SseEmitter emitter) {
        // O add fica dentro do compute: feito fora, um remove concorrente podia
        // esvaziar e tirar a lista do mapa entre o get e o add, e o emitter novo
        // ficaria numa lista órfã, sem receber nada.
        emitters.compute(tenantId, (id, list) -> {
            CopyOnWriteArrayList<SseEmitter> target = list != null ? list : new CopyOnWriteArrayList<>();
            target.add(emitter);
            return target;
        });
        emitter.onCompletion(() -> remove(tenantId, emitter));
        // Completar no timeout encerra a requisição assíncrona normalmente; sem
        // isso o Spring despacha AsyncRequestTimeoutException para o handler.
        emitter.onTimeout(() -> {
            remove(tenantId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(tenantId, emitter));
    }

    int subscriberCount(UUID tenantId) {
        List<SseEmitter> list = emitters.get(tenantId);
        return list == null ? 0 : list.size();
    }

    public void publish(UUID tenantId, String event) {
        List<SseEmitter> list = emitters.get(tenantId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                // O EventSource descarta evento sem data, então data nunca vai vazio.
                emitter.send(SseEmitter.event().name(event).data(event));
            } catch (IOException | IllegalStateException e) {
                log.debug("Conexão SSE do tenant {} caiu; removendo", tenantId);
                remove(tenantId, emitter);
            }
        }
    }

    /**
     * Mantém a conexão viva atrás de proxies que derrubam conexão ociosa e
     * faz o envio falhar cedo para conexões mortas, que saem da lista.
     */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (UUID tenantId : emitters.keySet()) {
            publish(tenantId, "heartbeat");
        }
    }

    private void remove(UUID tenantId, SseEmitter emitter) {
        emitters.computeIfPresent(tenantId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
