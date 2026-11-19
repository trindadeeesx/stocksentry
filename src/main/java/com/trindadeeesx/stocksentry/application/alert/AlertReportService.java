package com.trindadeeesx.stocksentry.application.alert;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.trindadeeesx.stocksentry.application.common.BusinessRuleException;
import com.trindadeeesx.stocksentry.domain.alert.Alert;
import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertConfigRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.StockMovementRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.alert.ReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertReportService {

	private final AlertConfigRepository alertConfigRepository;
	private final AlertRepository alertRepository;
	private final ProductRepository productRepository;
	private final StockMovementRepository stockMovementRepository;
	private final SecurityUtils securityUtils;
	private final Resend resend;

	@Value("${resend.from}")
	private String from;

	public ReportResponse sendReport(int days) {
		if (days != 7 && days != 30) {
			throw new IllegalArgumentException("days must be 7 or 30");
		}

		UUID tenantId = securityUtils.getCurrentTenantId();
		List<String> recipients = alertConfigRepository.findAllByTenantIdAndActiveTrue(tenantId).stream()
				.filter(c -> c.getType() == AlertType.EMAIL)
				.map(AlertConfig::getDestination)
				.toList();
		if (recipients.isEmpty()) {
			throw new BusinessRuleException("Nenhum e-mail de alerta configurado");
		}

		LocalDateTime since = LocalDateTime.now().minusDays(days);
		List<Alert> alerts = alertRepository.findAllByTenantIdAndTriggeredAtAfter(tenantId, since);
		long sent = alerts.stream().filter(a -> a.getStatus() == AlertStatus.SENT).count();
		long failed = alerts.stream().filter(a -> a.getStatus() == AlertStatus.FAILED).count();
		List<Product> critical = productRepository.findCriticalByTenantId(tenantId);
		long movements = stockMovementRepository.countByTenantIdAndCreatedAtAfter(tenantId, since);

		String subject = "Relatório de estoque — últimos " + days + " dias";
		String html = buildHtml(days, alerts.size(), sent, failed, critical, movements);

		for (String recipient : recipients) {
			try {
				resend.emails().send(CreateEmailOptions.builder()
						.from(from)
						.to(recipient)
						.subject(subject)
						.html(html)
						.build());
			} catch (Exception e) {
				log.error("Falha ao enviar relatório para {}", recipient, e);
				throw new BusinessRuleException("Falha ao enviar o relatório por e-mail");
			}
		}

		return ReportResponse.builder()
				.message("Relatório dos últimos " + days + " dias enviado para " + recipients.size() + " destinatário(s).")
				.timestamp(LocalDateTime.now())
				.build();
	}

	private String buildHtml(int days, int total, long sent, long failed, List<Product> critical, long movements) {
		StringBuilder rows = new StringBuilder();
		for (Product p : critical) {
			rows.append("<tr><td>%s</td><td>%s</td><td>%s / %s %s</td></tr>".formatted(
					HtmlUtils.htmlEscape(p.getName()),
					HtmlUtils.htmlEscape(String.valueOf(p.getSku())),
					p.getCurrentStock(), p.getMinStock(),
					HtmlUtils.htmlEscape(String.valueOf(p.getUnit()))));
		}
		String criticalBlock = critical.isEmpty()
				? "<p>Nenhum produto em estoque crítico agora.</p>"
				: "<table><tr><th>Produto</th><th>SKU</th><th>Atual / Mínimo</th></tr>" + rows + "</table>";

		return """
				<h2>Relatório de estoque — últimos %d dias</h2>
				<p>Alertas no período: <strong>%d</strong> (enviados: %d, falhos: %d)</p>
				<p>Movimentações de estoque: <strong>%d</strong></p>
				<h3>Produtos críticos agora</h3>
				%s
				""".formatted(days, total, sent, failed, movements, criticalBlock);
	}
}
