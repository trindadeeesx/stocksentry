package com.trindadeeesx.stocksentry.web.dto.push;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscribeRequest {
	@NotBlank
	private String endpoint;
	
	@NotBlank
	private String p256dh;
	
	@NotBlank
	private String auth;
	
	private String deviceName;
}
