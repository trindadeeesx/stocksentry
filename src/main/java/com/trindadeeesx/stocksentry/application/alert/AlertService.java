package com.trindadeeesx.stocksentry.application.alert;

import com.trindadeeesx.stocksentry.application.common.NotFoundException;
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
import com.trindadeeesx.stocksentry.infraestructure.security.SecurityUtils;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigRequest;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertConfigResponse;
import com.trindadeeesx.stocksentry.web.dto.alert.AlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {
    private static final int COOLDOWN_MINUTES = 30;

    private final AlertConfigRepository alertConfigRepository;
    private final AlertRepository alertRepository;
    private final SecurityUtils securityUtils;
    private final Resend resend;
    private final PushNotificationService pushNotificationService;

    @Value("${resend.from}")
    private String from;
    private final ProductRepository productRepository;

    public AlertConfigResponse createConfig(AlertConfigRequest request) {
        AlertConfig config = alertConfigRepository.save(
                AlertConfig.builder()
                        .tenant(securityUtils.getCurrentUser().getTenant())
                        .type(request.getType())
                        .destination(request.getDestination() != null ? request.getDestination() : "")
                        .active(true)
                        .build()
        );
        return toConfigResponse(config);
    }

    public List<AlertConfigResponse> findConfigs() {
        return alertConfigRepository
                .findAllByTenantIdAndActiveTrue(securityUtils.getCurrentTenantId())
                .stream().map(this::toConfigResponse).toList();
    }

    public void deleteConfig(UUID id) {
        UUID tenantId = securityUtils.getCurrentTenantId();
        AlertConfig config = alertConfigRepository.findById(id)
                .filter(c -> c.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Config not found"));
        config.setActive(false);
        alertConfigRepository.save(config);
    }

    public Page<AlertResponse> findAlertHistory(Pageable pageable) {
        return alertRepository
                .findAllByTenantIdOrderByTriggeredAtDesc(securityUtils.getCurrentTenantId(), pageable)
                .map(this::toAlertResponse);
    }

    /**
     * Chamado depois do commit da movimentação (ver {@link StockAlertListener}),
     * em outra thread e outra transação. Recarrega o produto em vez de confiar
     * numa entidade vinda de outra thread e nunca salva o Product inteiro: a
     * única escrita no produto é a reserva atômica de lastAlert.
     *
     * Regra de disparo:
     * - produto voltou ao normal antes do alerta rodar: não dispara
     * - primeira vez abaixo do mínimo (lastAlert == null): dispara
     * - já alertado há menos de 30 min: não dispara
     * - cooldown expirado: dispara
     */
    @Transactional
    public void processStockAlert(UUID productId) {
        Optional<Product> found = productRepository.findWithTenantById(productId);
        if (found.isEmpty()) {
            log.warn("Alerta ignorado: produto {} não existe mais", productId);
            return;
        }

        Product product = found.get();
        if (!product.isBelowMinStock()) {
            return;
        }

        List<AlertConfig> configs = alertConfigRepository
                .findAllByTenantIdAndActiveTrue(product.getTenant().getId());
        if (configs.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int claimed = productRepository.claimAlertSlot(productId, now, now.minusMinutes(COOLDOWN_MINUTES));
        if (claimed == 0) {
            return;
        }

        for (AlertConfig config : configs) {
            AlertStatus status = AlertStatus.FAILED;
            try {
                switch (config.getType()) {
                    case EMAIL -> sendEmail(config.getDestination(), product);
                    case PUSH -> pushNotificationService.sendToAllDevices(
                            product.getTenant().getId(),
                            "⚠️ Estoque crítico: " + product.getName(),
                            "Atual: " + product.getCurrentStock() + " | Mínimo: " + product.getMinStock()
                    );
                }
                status = AlertStatus.SENT;
            } catch (Exception e) {
                log.error("Falha ao disparar alerta {} do produto {} para {}",
                        config.getType(), productId, config.getDestination(), e);
            }

            alertRepository.save(
                    Alert.builder()
                            .tenant(product.getTenant())
                            .product(product)
                            .type(config.getType())
                            .destination(config.getDestination())
                            .triggeredAt(now)
                            .status(status)
                            .build()
            );
        }
    }

    public void resetAlert(Product product) {
        product.setLastAlert(null);
        productRepository.save(product);
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