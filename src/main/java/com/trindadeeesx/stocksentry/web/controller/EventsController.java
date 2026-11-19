package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.events.SseHub;
import com.trindadeeesx.stocksentry.application.events.SseTicketService;
import com.trindadeeesx.stocksentry.application.events.SseTicketService.TicketOwner;
import com.trindadeeesx.stocksentry.domain.user.User;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventsController {

    private final SseTicketService ticketService;
    private final SseHub sseHub;
    private final SecurityUtils securityUtils;

    /** Autenticado pelo JWT normal. Devolve um ticket de 30s e uso único. */
    @PostMapping("/ticket")
    public Map<String, String> ticket() {
        User user = securityUtils.getCurrentUser();
        return Map.of("ticket", ticketService.issue(user.getId(), user.getTenant().getId()));
    }

    /** Público no SecurityConfig: o ticket é a autenticação. */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        TicketOwner owner = ticketService.consume(ticket)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return sseHub.subscribe(owner.tenantId());
    }
}
