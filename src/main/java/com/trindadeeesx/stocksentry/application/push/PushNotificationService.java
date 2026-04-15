package com.trindadeeesx.stocksentry.application.push;

import com.trindadeeesx.stocksentry.domain.push.PushSubscription;
import com.trindadeeesx.stocksentry.domain.push.VapidPublicKeyResponse;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.infraestructure.persistence.PushSubscriptionRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.push.PushSubscribeRequest;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final SecurityUtils securityUtils;

    @Value("${vapid.public-key}")
    private String vapidPublicKey;

    @Value("${vapid.private-key}")
    private String vapidPrivateKey;

    @Value("${vapid.subject}")
    private String vapidSubject;

    public void subscribe(PushSubscribeRequest request) {
        Tenant tenant = securityUtils.getCurrentUser().getTenant();

        subscriptionRepository.findByEndpoint(request.getEndpoint())
                .ifPresentOrElse(
                        existing -> {
                            existing.setP256dh(request.getP256dh());
                            existing.setAuthKey(request.getAuth());
                            existing.setDeviceName(request.getDeviceName());
                            subscriptionRepository.save(existing);
                        },
                        () -> subscriptionRepository.save(
                                PushSubscription.builder()
                                        .tenant(tenant)
                                        .endpoint(request.getEndpoint())
                                        .p256dh(request.getP256dh())
                                        .authKey(request.getAuth())
                                        .deviceName(request.getDeviceName())
                                        .build()
                        )
                );
    }

    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }

    public VapidPublicKeyResponse getPublicKey() {
        return VapidPublicKeyResponse.builder()
                .publicKey(vapidPublicKey)
                .build();
    }

    @Async
    public void sendToAllDevices(UUID tenantId, String title, String body) {
        List<PushSubscription> subscriptions = subscriptionRepository.findAllByTenantId(tenantId);

        PushService pushService;
        try {
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PushService", e);
        }

        String payload = """
                {"title":"%s","body":"%s"}
                """.formatted(title, body);

        for (PushSubscription sub : subscriptions) {
            try {
                Subscription subscription = new Subscription(
                        sub.getEndpoint(),
                        new Subscription.Keys(sub.getP256dh(), sub.getAuthKey())
                );
                pushService.send(new Notification(subscription, payload));
            } catch (Exception e) {
                // log e continua pros outros dispositivos
                System.out.println("Falha ao enviar push para " + sub.getEndpoint() + ": " + e.getMessage());
            }
        }
    }
}