package com.trindadeeesx.stocksentry.application.alert;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.trindadeeesx.stocksentry.application.push.PushNotificationService;
import com.trindadeeesx.stocksentry.domain.alert.Alert;
import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import com.trindadeeesx.stocksentry.domain.alert.AlertType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
	
	public Page<AlertResponse> findAlertHistory(Pageable pageable) {
		return alertRepository.findAllByOrderByTriggeredAtDesc(pageable).map(this::toAlertResponse);
	}
	
	public List<AlertResponse> findRecentAlerts(int limit) {
		return alertRepository.findAllByOrderByTriggeredAtDesc(Pageable.ofSize(limit))
			.stream()
			.map(this::toAlertResponse)
			.toList();
	}
	
	@Scheduled(cron = "0 0 8 * * MON")
	public void sendWeeklyReport() {
		sendReport(7);
	}
	
	@Scheduled(cron = "0 0 8 1 * *")
	public void sendMonthlyReport() {
		sendReport(30);
	}
	
	public void sendReport(int days) {
		LocalDateTime since = LocalDateTime.now().minusDays(days);
		List<Alert> alerts = alertRepository.findByTriggeredAtAfter(since);
		List<Product> currentCritical = productRepository.findCritical();
		
		List<AlertConfig> emailConfigs = alertConfigRepository.findAllByActiveTrue().stream()
			.filter(c -> c.getType() == AlertType.EMAIL)
			.toList();
		
		if (emailConfigs.isEmpty()) {
			log.warn("No email configs found, skipping report.");
			return;
		}
		
		String subject = days <= 7
			? "📊 Relatório Semanal Meiliy - Alertas de Estoque"
			: "📊 Relatório Mensal Meiliy - Alertas de Estoque";
		String html = buildReportHtml(days, since, alerts, currentCritical);
		
		for (AlertConfig config : emailConfigs) {
			if (config.getDestination().isBlank()) continue;
			try {
				resend.emails().send(CreateEmailOptions.builder()
					.from(from)
					.to(config.getDestination())
					.subject(subject)
					.html(html)
					.build());
				log.info("Report ({} days) sent to {}.", days, maskEmail(config.getDestination()));
			} catch (Exception e) {
				log.error("Failed to send report to {}", maskEmail(config.getDestination()), e);
			}
		}
	}
	
	public void processStockAlert(List<Product> criticalProducts) {
		if (criticalProducts.isEmpty()) return;
		
		List<Product> toAlert = criticalProducts.stream()
			.filter(this::shouldDispatch)
			.toList();
		
		if (toAlert.isEmpty()) {
			log.debug("All critical products are within cooldown, skipping alert.");
			return;
		}
		
		log.debug("{} products are below min.", toAlert.size());
		
		List<AlertConfig> configs = alertConfigRepository.findAllByActiveTrue();
		
		if (configs.isEmpty()) {
			log.debug("No alerts config found, skipping alert.");
			return;
		}
		
		for (AlertConfig config : configs) {
			AlertStatus status = AlertStatus.FAILED;
			try {
				switch (config.getType()) {
					case EMAIL -> sendBatchEmail(config.getDestination(), toAlert);
					case PUSH -> pushNotificationService.sendToAllDevices(
						"⚠️ " + toAlert.size() + " produto(s) com estoque crítico!",
						toAlert.stream()
							.map(p -> p.getName() + ": " + p.getCurrentStock())
							.limit(3)
							.reduce("", (a, b) -> a.isEmpty() ? b : a + " | ") +
							(toAlert.size() > 3 ? " e mais " + (toAlert.size() - 3) + "..." : "")
					);
				}
				status = AlertStatus.SENT;
			} catch (Exception e) {
				log.error("Failed to dispatch {} alert", config.getType(), e);
			}
			
			AlertStatus finalStatus = status;
			toAlert.forEach(product ->
				alertRepository.save(Alert.builder()
					.product(product)
					.type(config.getType())
					.destination(config.getDestination())
					.triggeredAt(LocalDateTime.now())
					.status(finalStatus)
					.build())
			);
		}
		
		LocalDateTime now = LocalDateTime.now();
		toAlert.forEach(product -> {
			product.setLastAlert(now);
			productRepository.save(product);
		});
		
		log.info("Batch alert sent for {} product(s).", toAlert.size());
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
	
	
	private void sendBatchEmail(String destination, List<Product> products) throws Exception {
		if (destination == null || destination.isBlank()) {
			throw new IllegalStateException("EMAIL alert config has no destination address configured");
		}
		CreateEmailOptions emailRequest = CreateEmailOptions.builder()
			.from(from)
			.to(destination)
			.subject("⚠️ Estoque Meiliy - " + products.size() + " produto(s) com estoque crítico!")
			.html(buildBatchEmailHtml(products))
			.build();
		
		resend.emails().send(emailRequest);
	}
	
	private String buildReportHtml(int days, LocalDateTime since, List<Alert> alerts, List<Product> critical) {
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
		long sent = alerts.stream().filter(a -> a.getStatus() == AlertStatus.SENT).count();
		long failed = alerts.size() - sent;
		
		Map<String, Long> byProduct = alerts.stream()
			.collect(Collectors.groupingBy(a -> a.getProduct().getName(), Collectors.counting()));
		
		StringBuilder topRows = new StringBuilder();
		byProduct.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
			.limit(10)
			.forEach(e -> topRows.append("""
				<tr>
				  <td style="padding:6px 8px;border-bottom:1px solid #eee">%s</td>
				  <td style="padding:6px 8px;border-bottom:1px solid #eee;text-align:center">%d</td>
				</tr>
				""".formatted(HtmlUtils.htmlEscape(e.getKey()), e.getValue())));

		StringBuilder criticalRows = new StringBuilder();
		critical.forEach(p -> criticalRows.append("""
			<tr>
			  <td style="padding:6px 8px;border-bottom:1px solid #eee">%s</td>
			  <td style="padding:6px 8px;border-bottom:1px solid #eee;text-align:center">%s</td>
			  <td style="padding:6px 8px;border-bottom:1px solid #eee;text-align:center;color:#e53e3e"><strong>%s</strong></td>
			  <td style="padding:6px 8px;border-bottom:1px solid #eee;text-align:center">%s</td>
			</tr>
			""".formatted(HtmlUtils.htmlEscape(p.getName()), HtmlUtils.htmlEscape(p.getSku()),
				p.getCurrentStock(), p.getMinStock())));
		
		return """
			<div style="font-family:sans-serif;max-width:700px;margin:0 auto;color:#333">
			  <h2 style="color:#2d3748">📊 Relatório de Estoque — últimos %d dias</h2>
			  <p style="color:#666">Período: %s até %s</p>
			
			  <h3 style="color:#2d3748;border-bottom:2px solid #eee;padding-bottom:4px">Resumo de alertas</h3>
			  <table style="border-collapse:collapse;font-size:14px;margin-bottom:24px">
			    <tr><td style="padding:4px 12px 4px 0">Total de alertas:</td><td><strong>%d</strong></td></tr>
			    <tr><td style="padding:4px 12px 4px 0">Enviados com sucesso:</td><td style="color:#38a169"><strong>%d</strong></td></tr>
			    <tr><td style="padding:4px 12px 4px 0">Falhos:</td><td style="color:#e53e3e"><strong>%d</strong></td></tr>
			  </table>
			
			  %s
			
			  %s
			
			  <p style="margin-top:24px;color:#999;font-size:12px">Gerado automaticamente pelo StockSentry.</p>
			</div>
			""".formatted(
			days,
			since.format(fmt), LocalDateTime.now().format(fmt),
			alerts.size(), sent, failed,
			topRows.isEmpty() ? "" : """
				<h3 style="color:#2d3748;border-bottom:2px solid #eee;padding-bottom:4px">Produtos com mais alertas no período</h3>
				<table style="width:100%%;border-collapse:collapse;font-size:14px;margin-bottom:24px">
				  <thead><tr style="background:#f7f7f7">
				    <th style="padding:8px;text-align:left">Produto</th>
				    <th style="padding:8px">Alertas</th>
				  </tr></thead>
				  <tbody>%s</tbody>
				</table>
				""".formatted(topRows),
			critical.isEmpty() ? "<p style=\"color:#38a169\">✅ Nenhum produto crítico no momento.</p>" : """
				<h3 style="color:#e53e3e;border-bottom:2px solid #eee;padding-bottom:4px">⚠️ Produtos críticos agora (%d)</h3>
				<table style="width:100%%;border-collapse:collapse;font-size:14px">
				  <thead><tr style="background:#f7f7f7">
				    <th style="padding:8px;text-align:left">Produto</th>
				    <th style="padding:8px">SKU</th>
				    <th style="padding:8px">Estoque atual</th>
				    <th style="padding:8px">Mínimo</th>
				  </tr></thead>
				  <tbody>%s</tbody>
				</table>
				""".formatted(critical.size(), criticalRows)
		);
	}
	
	private String buildBatchEmailHtml(List<Product> products) {
		StringBuilder rows = new StringBuilder();
		for (Product p : products) {
			rows.append("""
				<tr>
				  <td style="padding:8px;border-bottom:1px solid #eee">%s</td>
				  <td style="padding:8px;border-bottom:1px solid #eee;text-align:center">%s</td>
				  <td style="padding:8px;border-bottom:1px solid #eee;text-align:center;color:#e53e3e"><strong>%s %s</strong></td>
				  <td style="padding:8px;border-bottom:1px solid #eee;text-align:center">%s %s</td>
				</tr>
				""".formatted(
				HtmlUtils.htmlEscape(p.getName()), HtmlUtils.htmlEscape(p.getSku()),
				p.getCurrentStock(), p.getUnit(),
				p.getMinStock(), p.getUnit()
			));
		}
		
		return """
			<div style="font-family:sans-serif;max-width:900px;margin:0 auto">
			  <h2 style="color:#e53e3e">⚠️ Estoque crítico detectado</h2>
			  <p>Os seguintes produtos estão abaixo do estoque mínimo:</p>
			  <table style="width:100%%;border-collapse:collapse;font-size:14px">
			    <thead>
			      <tr style="background:#f7f7f7">
			        <th style="padding:8px;text-align:left">Produto</th>
			        <th style="padding:8px">SKU</th>
			        <th style="padding:8px">Estoque atual</th>
			        <th style="padding:8px">Mínimo</th>
			      </tr>
			    </thead>
			    <tbody>%s</tbody>
			  </table>
			  <p style="margin-top:20px;color:#666;font-size:13px">
			    Próximo alerta em 30 minutos se os estoques não forem repostos.
			  </p>
			</div>
			""".formatted(rows);
	}
	
	private String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 1) return "***";
		return email.charAt(0) + "***" + email.substring(at);
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