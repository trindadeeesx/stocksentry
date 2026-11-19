package com.trindadeeesx.stocksentry.application.alert;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.trindadeeesx.stocksentry.application.push.PushNotificationService;
import com.trindadeeesx.stocksentry.domain.alert.Alert;
import com.trindadeeesx.stocksentry.domain.alert.AlertConfig;
import com.trindadeeesx.stocksentry.domain.alert.AlertStatus;
import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import com.trindadeeesx.stocksentry.domain.product.Product;
import com.trindadeeesx.stocksentry.domain.product.UnitType;
import com.trindadeeesx.stocksentry.domain.tenant.Tenant;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertConfigRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.AlertRepository;
import com.trindadeeesx.stocksentry.infraestructure.persistence.ProductRepository;
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

	@Mock AlertConfigRepository alertConfigRepository;
	@Mock AlertRepository alertRepository;
	@Mock SecurityUtils securityUtils;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS) Resend resend;
	@Mock PushNotificationService pushNotificationService;
	@Mock ProductRepository productRepository;

	@InjectMocks AlertService alertService;

	Tenant tenant;
	Product product;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(alertService, "from", "alertas@exemplo.com");
		tenant = Tenant.builder().id(UUID.randomUUID()).name("Loja").slug("loja").active(true).build();
		product = Product.builder()
				.id(UUID.randomUUID())
				.tenant(tenant)
				.name("Shampoo 300ml")
				.sku("SH-300")
				.unit(UnitType.UN)
				.currentStock(new BigDecimal("2"))
				.minStock(new BigDecimal("5"))
				.active(true)
				.build();
	}

	/**
	 * O alerta roda depois do commit, em outra thread. Se nesse meio-tempo entrou
	 * mercadoria e o produto saiu da zona crítica, mandar alerta seria falso positivo.
	 */
	@Test
	void shouldNotDispatch_whenProductRecoveredBeforeAlertRan() {
		product.setCurrentStock(new BigDecimal("8"));
		when(productRepository.findWithTenantById(product.getId())).thenReturn(Optional.of(product));

		alertService.processStockAlert(product.getId());

		verify(productRepository, never()).claimAlertSlot(any(), any(), any());
		verify(alertRepository, never()).save(any());
	}

	/**
	 * Duas movimentações seguidas geram dois eventos. Só quem ganha a reserva
	 * atômica de lastAlert dispara; o outro (ou um evento dentro do cooldown) sai calado.
	 */
	@Test
	void shouldNotDispatch_whenAlertSlotIsAlreadyTaken() {
		when(productRepository.findWithTenantById(product.getId())).thenReturn(Optional.of(product));
		when(alertConfigRepository.findAllByTenantIdAndActiveTrue(tenant.getId()))
				.thenReturn(List.of(config(AlertType.EMAIL, "dono@loja.com")));
		when(productRepository.claimAlertSlot(eq(product.getId()), any(), any())).thenReturn(0);

		alertService.processStockAlert(product.getId());

		verify(alertRepository, never()).save(any());
		verify(pushNotificationService, never()).sendToAllDevices(any(), anyString(), anyString());
	}

	/**
	 * A janela de cooldown passada para a reserva é de 30 minutos antes de agora.
	 * Além disso, o Product inteiro nunca é salvo: salvar a cópia lida aqui
	 * sobrescreveria um currentStock que pode ter mudado desde a leitura.
	 */
	@Test
	void shouldDispatchAndRecord_whenSlotIsClaimed_withoutSavingProduct() {
		when(productRepository.findWithTenantById(product.getId())).thenReturn(Optional.of(product));
		when(alertConfigRepository.findAllByTenantIdAndActiveTrue(tenant.getId()))
				.thenReturn(List.of(config(AlertType.PUSH, "")));
		ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
		when(productRepository.claimAlertSlot(eq(product.getId()), now.capture(), threshold.capture())).thenReturn(1);

		alertService.processStockAlert(product.getId());

		assertThat(threshold.getValue()).isEqualTo(now.getValue().minusMinutes(30));
		verify(pushNotificationService).sendToAllDevices(eq(tenant.getId()), anyString(), anyString());
		ArgumentCaptor<Alert> saved = ArgumentCaptor.forClass(Alert.class);
		verify(alertRepository).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(AlertStatus.SENT);
		verify(productRepository, never()).save(any());
	}

	/**
	 * Falha num canal (e-mail fora do ar) é registrada como FAILED e não impede
	 * os outros canais de receberem o alerta.
	 */
	@Test
	void shouldRecordFailureAndKeepGoing_whenOneChannelFails() throws Exception {
		when(productRepository.findWithTenantById(product.getId())).thenReturn(Optional.of(product));
		when(alertConfigRepository.findAllByTenantIdAndActiveTrue(tenant.getId()))
				.thenReturn(List.of(config(AlertType.EMAIL, "dono@loja.com"), config(AlertType.PUSH, "")));
		when(productRepository.claimAlertSlot(eq(product.getId()), any(), any())).thenReturn(1);
		Emails emails = resend.emails();
		doThrow(new RuntimeException("provedor de e-mail fora do ar")).when(emails).send(any());

		alertService.processStockAlert(product.getId());

		ArgumentCaptor<Alert> saved = ArgumentCaptor.forClass(Alert.class);
		verify(alertRepository, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(Alert::getStatus)
				.containsExactly(AlertStatus.FAILED, AlertStatus.SENT);
		verify(pushNotificationService).sendToAllDevices(eq(tenant.getId()), anyString(), anyString());
	}

	@Test
	void shouldIgnore_whenProductNoLongerExists() {
		when(productRepository.findWithTenantById(product.getId())).thenReturn(Optional.empty());

		alertService.processStockAlert(product.getId());

		verify(alertConfigRepository, never()).findAllByTenantIdAndActiveTrue(any());
		verify(alertRepository, never()).save(any());
	}

	/**
	 * A config de alerta pertence a um tenant. Um usuário de outra empresa que
	 * conheça o ID não pode desativá-la: responde como inexistente e não grava nada.
	 */
	@Test
	void shouldNotDeleteConfig_whenItBelongsToAnotherTenant() {
		AlertConfig alheia = config(AlertType.EMAIL, "dono@loja.com");
		when(securityUtils.getCurrentTenantId()).thenReturn(UUID.randomUUID());
		when(alertConfigRepository.findById(alheia.getId())).thenReturn(Optional.of(alheia));

		assertThatThrownBy(() -> alertService.deleteConfig(alheia.getId()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Config not found");

		assertThat(alheia.isActive()).isTrue();
		verify(alertConfigRepository, never()).save(any());
	}

	@Test
	void shouldDeactivateConfig_whenItBelongsToCurrentTenant() {
		AlertConfig propria = config(AlertType.EMAIL, "dono@loja.com");
		when(securityUtils.getCurrentTenantId()).thenReturn(tenant.getId());
		when(alertConfigRepository.findById(propria.getId())).thenReturn(Optional.of(propria));

		alertService.deleteConfig(propria.getId());

		assertThat(propria.isActive()).isFalse();
		verify(alertConfigRepository).save(propria);
	}

	AlertConfig config(AlertType type, String destination) {
		return AlertConfig.builder()
				.id(UUID.randomUUID())
				.tenant(tenant)
				.type(type)
				.destination(destination)
				.active(true)
				.build();
	}
}
