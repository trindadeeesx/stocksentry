package com.trindadeeesx.stocksentry.application.alert;

import com.trindadeeesx.stocksentry.domain.stock.StockBelowMinEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockAlertListenerTest {

	/**
	 * Trava o contrato do listener: assíncrono e só depois do commit. Se alguém
	 * voltar para @EventListener, o alerta volta a sair para movimentações que
	 * deram rollback.
	 */
	@Test
	void shouldListenAsynchronouslyAndOnlyAfterCommit() throws NoSuchMethodException {
		Method method = StockAlertListener.class.getMethod("onStockBelowMin", StockBelowMinEvent.class);

		TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		assertThat(method.getAnnotation(Async.class)).isNotNull();
	}

	@Test
	void shouldDelegateProductIdToAlertService() {
		AlertService alertService = mock(AlertService.class);
		UUID productId = UUID.randomUUID();

		new StockAlertListener(alertService).onStockBelowMin(new StockBelowMinEvent(productId));

		verify(alertService).processStockAlert(productId);
	}
}
