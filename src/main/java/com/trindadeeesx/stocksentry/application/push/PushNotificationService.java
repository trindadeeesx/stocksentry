package com.trindadeeesx.stocksentry.application.push;

import com.trindadeeesx.stocksentry.domain.push.PushSubscription;
import com.trindadeeesx.stocksentry.domain.push.VapidPublicKeyResponse;
import com.trindadeeesx.stocksentry.infraestructure.persistence.PushSubscriptionRepository;
import com.trindadeeesx.stocksentry.web.dto.push.PushSubscribeRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {
	
	private final PushSubscriptionRepository subscriptionRepository;
	
	private final ObjectMapper objectMapper;

	@Value("${vapid.public-key}")
	private String vapidPublicKey;
	
	@Value("${vapid.private-key}")
	private String vapidPrivateKey;
	
	@Value("${vapid.subject}")
	private String vapidSubject;
	
	private PushService pushService;

	@PostConstruct
	public void init() {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		try {
			this.pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize PushService", e);
		}
	}
	
	public void subscribe(PushSubscribeRequest request) {
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
	
	public void sendToAllDevices(String title, String body) {
		List<PushSubscription> subscriptions = subscriptionRepository.findAll();
		
		if (subscriptions.isEmpty()) {
			log.debug("No push subscriptions registered, skipping notifications.");
			return;
		}
		
		String payload;
		try {
			payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body));
		} catch (Exception e) {
			log.error("Failed to build push payload", e);
			return;
		}
		
		for (PushSubscription sub : subscriptions) {
			try {
				Subscription subscription = new Subscription(
					sub.getEndpoint(),
					new Subscription.Keys(sub.getP256dh(), sub.getAuthKey())
				);
				pushService.send(new Notification(subscription, payload));
			} catch (Exception e) {
				log.warn("Failed to send push notifications to {}: {}", sub.getEndpoint(), e.getMessage());
			}
		}
	}
}