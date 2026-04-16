package com.trindadeeesx.stocksentry.web.dto.alert;

import com.trindadeeesx.stocksentry.domain.alert.AlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertConfigRequest {
    @NotNull
    private AlertType type;

    private String destination;
}
