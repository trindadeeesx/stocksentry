package com.trindadeeesx.stocksentry.web.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushUnsubscribeRequest {
	@NotBlank
	@Size(max = 500)
	private String endpoint;
}
