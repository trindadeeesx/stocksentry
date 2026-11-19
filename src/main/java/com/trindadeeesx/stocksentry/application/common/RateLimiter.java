package com.trindadeeesx.stocksentry.application.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite simples por chave em janela fixa, em memória. Basta enquanto o back
 * roda numa instância só (mesma premissa do SSE).
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Conta um uso de {@code key}; acima de {@code limit} na janela, lança 429. */
    public void check(String key, int limit, Duration window, String message) {
        Instant now = clock.instant();
        windows.values().removeIf(w -> !now.isBefore(w.end()));
        Window current = windows.compute(key, (k, w) ->
                w == null || !now.isBefore(w.end()) ? new Window(now.plus(window), 1) : new Window(w.end(), w.count() + 1));
        if (current.count() > limit) {
            throw new TooManyRequestsException(message);
        }
    }

    private record Window(Instant end, int count) {
    }
}
