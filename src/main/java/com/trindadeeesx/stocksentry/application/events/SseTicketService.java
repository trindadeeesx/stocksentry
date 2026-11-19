package com.trindadeeesx.stocksentry.application.events;

import com.trindadeeesx.stocksentry.application.common.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tickets de uso único para abrir o stream SSE.
 *
 * O EventSource do navegador não manda header, então a credencial tem que ir
 * na URL. Mandar o JWT ali vazaria um token de longa duração para logs de
 * proxy e para o histórico. O ticket vale 30s e só uma vez.
 *
 * Fica em memória: basta enquanto o back roda numa instância só.
 */
@Service
public class SseTicketService {

    static final Duration TTL = Duration.ofSeconds(30);
    /** Tickets ainda não usados por usuário; o front precisa de um por conexão. */
    static final int MAX_PENDING_PER_USER = 5;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public SseTicketService(Clock clock) {
        this.clock = clock;
    }

    public String issue(UUID userId, UUID tenantId) {
        Instant now = clock.instant();
        // Limpa os que expiraram sem ser usados, para o mapa não crescer sem fim.
        tickets.values().removeIf(e -> e.isExpired(now));
        long pending = tickets.values().stream().filter(e -> e.owner().userId().equals(userId)).count();
        if (pending >= MAX_PENDING_PER_USER) {
            throw new TooManyRequestsException("Muitos tickets pendentes; use os que já foram emitidos");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Entry(new TicketOwner(userId, tenantId), now.plus(TTL)));
        return ticket;
    }

    public Optional<TicketOwner> consume(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.owner());
    }

    public record TicketOwner(UUID userId, UUID tenantId) {
    }

    private record Entry(TicketOwner owner, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
