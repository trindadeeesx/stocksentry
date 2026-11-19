package com.trindadeeesx.stocksentry.application.alert;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.trindadeeesx.stocksentry.application.common.BusinessRuleException;
import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertConfigRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.StockMovementRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.alert.ReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertReportServiceTest {

	@Mock AlertConfigRepository alertConfigRepository;
	@Mock AlertRepository alertRepository;
	@Mock ProductRepository productRepository;
	@Mock StockMovementRepository stockMovementRepository;
	@Mock SecurityUtils securityUtils;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS) Resend resend;

	@InjectMocks AlertReportService service;

	UUID tenantId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "from", "alertas@exemplo.com");
	}

	private AlertConfig config(AlertType type, String destination) {
		return AlertConfig.builder().type(type).destination(destination).active(true).build();
	}

	private void stubTenantData(List<AlertConfig> configs) {
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(alertConfigRepository.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(configs);
		when(alertRepository.findAllByTenantIdAndTriggeredAtAfter(any(), any())).thenReturn(List.of());
		when(stockMovementRepository.countByTenantIdAndCreatedAtAfter(any(), any())).thenReturn(12L);
		when(productRepository.findCriticalByTenantId(tenantId)).thenReturn(List.of(
				Product.builder().name("Shampoo <b>300ml</b>").sku("SH-300").unit(UnitType.UN)
						.currentStock(new BigDecimal("2")).minStock(new BigDecimal("5")).build()));
	}

	@Test
	void shouldSendOneEmailPerRecipient() throws Exception {
		stubTenantData(List.of(config(AlertType.EMAIL, "a@x.com"), config(AlertType.EMAIL, "b@x.com"),
				config(AlertType.PUSH, "")));
		Emails emails = resend.emails();

		service.sendReport(7);

		ArgumentCaptor<CreateEmailOptions> captor = ArgumentCaptor.forClass(CreateEmailOptions.class);
		verify(emails, times(2)).send(captor.capture());
		List<CreateEmailOptions> sent = captor.getAllValues();
		assertThat(sent).extracting(o -> o.getTo().get(0)).containsExactly("a@x.com", "b@x.com");
		assertThat(sent.get(0).getSubject()).isEqualTo("Relatório de estoque — últimos 7 dias");
		assertThat(sent.get(0).getFrom()).isEqualTo("alertas@exemplo.com");
		assertThat(sent.get(0).getHtml()).contains("Shampoo &lt;b&gt;300ml&lt;/b&gt;").contains("SH-300");
	}

	@Test
	void shouldRejectInvalidDays() {
		assertThatThrownBy(() -> service.sendReport(15))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("days must be 7 or 30");
	}

	@Test
	void shouldFail422_whenNoEmailConfig() throws Exception {
		when(securityUtils.getCurrentTenantId()).thenReturn(tenantId);
		when(alertConfigRepository.findAllByTenantIdAndActiveTrue(tenantId))
				.thenReturn(List.of(config(AlertType.PUSH, "")));
		Emails emails = resend.emails();

		assertThatThrownBy(() -> service.sendReport(30))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("Nenhum e-mail de alerta configurado");
		verify(emails, never()).send(any(CreateEmailOptions.class));
	}

	@Test
	void shouldReturnMessageWithRecipientCount() {
		stubTenantData(List.of(config(AlertType.EMAIL, "a@x.com"), config(AlertType.EMAIL, "b@x.com")));

		ReportResponse response = service.sendReport(30);

		assertThat(response.getMessage()).isEqualTo("Relatório dos últimos 30 dias enviado para 2 destinatário(s).");
		assertThat(response.getTimestamp()).isNotNull();
	}
}
