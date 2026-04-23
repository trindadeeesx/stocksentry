package com.trindadeeesx.stocksentry.web.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushSubscribeRequest {
	@NotBlank
	@Size(max = 500)
	private String endpoint;

	@NotBlank
	@Size(max = 256)
	private String p256dh;

	@NotBlank
	@Size(max = 128)
	private String auth;

	@Size(max = 100)
	private String deviceName;
}
