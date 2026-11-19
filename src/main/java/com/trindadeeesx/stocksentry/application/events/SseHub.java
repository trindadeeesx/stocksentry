package com.trindadeeesx.stocksentry.application.events;

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

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
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
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(e -> remove(tenantId, emitter));
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
