package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.push.PushNotificationService;
import com.trindadeeesx.stocksentry.domain.push.VapidPublicKeyResponse;
import com.trindadeeesx.stocksentry.web.dto.push.PushSubscribeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushNotificationService;

    @GetMapping("/vapid-key")
    public VapidPublicKeyResponse getVapidKey() {
        return pushNotificationService.getPublicKey();
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@RequestBody @Valid PushSubscribeRequest request) {
        pushNotificationService.subscribe(request);
    }

    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam String endpoint) {
        pushNotificationService.unsubscribe(endpoint);
    }
}