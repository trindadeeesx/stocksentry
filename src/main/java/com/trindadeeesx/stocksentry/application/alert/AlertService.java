package com.trindadeeesx.stocksentry.application.alert;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.trindadeeesx.stocksentry.application.push.PushNotificationService;
import com.trindadeeesx.stocksentry.domain.alert.Alert;
import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertConfigRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigRequest;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigResponse;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {
	private static final int COOLDOWN_MINUTES = 30;
	
	private final AlertConfigRepository alertConfigRepository;
	private final AlertRepository alertRepository;
	private final ProductRepository productRepository;
	private final Resend resend;
	private final PushNotificationService pushNotificationService;
	
	@Value("${resend.from}")
	private String from;
	
	public AlertConfigResponse createConfig(AlertConfigRequest request) {
		AlertConfig config = alertConfigRepository.save(
			AlertConfig.builder()
				.type(request.getType())
				.destination(request.getDestination() != null ? request.getDestination() : "")
				.active(true)
				.build()
		);
		return toConfigResponse(config);
	}
	
	public List<AlertConfigResponse> findConfigs() {
		return alertConfigRepository.findAllByActiveTrue().stream().map(this::toConfigResponse).toList();
	}
	
	public void deleteConfig(UUID id) {
		AlertConfig config = alertConfigRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Config not found"));
		config.setActive(false);
		alertConfigRepository.save(config);
	}
	
	public List<AlertResponse> findAlertHistory() {
		return alertRepository
			.stream().map(this::toAlertResponse).toList();
	}
	
	@Async
	public void processStockAlert(Product product) {
		if (!shouldDispatch(product)) {
			return;
		}
		
		List<AlertConfig> configs = alertConfigRepository.findAllByActiveTrue();
		
		if (configs.isEmpty()) {
			log.debug("No alerts config found, skipping alert for product '{}'.", product.getName());
			return;
		}
		
		for (AlertConfig config : configs) {
			AlertStatus status = AlertStatus.FAILED;
			try {
				switch (config.getType()) {
					case EMAIL -> sendEmail(config.getDestination(), product);
					case PUSH -> pushNotificationService.sendToAllDevices(
						"⚠️ Estoque crítico: " + product.getName(),
						"Atual: " + product.getCurrentStock() + " | Mínimo: " + product.getMinStock()
					);
				}
				status = AlertStatus.SENT;
			} catch (Exception e) {
				log.error("Failed to dispatch alert of type {} for product '{}': {}",
					config.getType(), product.getName(), e.getMessage());
			}
			
			alertRepository.save(
				Alert.builder()
					.product(product)
					.type(config.getType())
					.destination(config.getDestination())
					.triggeredAt(LocalDateTime.now())
					.status(status)
					.build()
			);
		}
		
		product.setLastAlert(LocalDateTime.now());
		productRepository.save(product);
	}
	
	public void resetAlert(Product product) {
		product.setLastAlert(null);
		productRepository.save(product);
	}
	
	private boolean shouldDispatch(Product product) {
		if (product.getLastAlert() == null) {
			return true;
		}
		
		return product.getLastAlert()
			.plusMinutes(COOLDOWN_MINUTES)
			.isBefore(LocalDateTime.now());
	}
	
	
	private void sendEmail(String destination, Product product) throws Exception {
		CreateEmailOptions emailRequest = CreateEmailOptions.builder()
			.from(from)
			.to(destination)
			.subject("⚠️ Estoque crítico: " + product.getName())
			.html(buildEmailHtml(product))
			.build();
		
		resend.emails().send(emailRequest);
	}
	
	private String buildEmailHtml(Product product) {
		return """
			<h2>Estoque crítico detectado</h2>
			<p>O produto <strong>%s</strong> (SKU: %s) está abaixo do mínimo.</p>
			<table>
			  <tr><td>Estoque atual:</td><td><strong>%s %s</strong></td></tr>
			  <tr><td>Estoque mínimo:</td><td><strong>%s %s</strong></td></tr>
			</table>
			<p>Acesse o sistema para reabastecer.</p>
			""".formatted(
			product.getName(),
			product.getSku(),
			product.getCurrentStock(), product.getUnit(),
			product.getMinStock(), product.getUnit()
		);
	}
	
	private AlertConfigResponse toConfigResponse(AlertConfig c) {
		return AlertConfigResponse.builder()
			.id(c.getId())
			.type(c.getType())
			.destination(c.getDestination())
			.active(c.isActive())
			.build();
	}
	
	private AlertResponse toAlertResponse(Alert a) {
		return AlertResponse.builder()
			.id(a.getId())
			.productName(a.getProduct().getName())
			.type(a.getType())
			.destination(a.getDestination())
			.status(a.getStatus())
			.triggeredAt(a.getTriggeredAt())
			.build();
	}
}