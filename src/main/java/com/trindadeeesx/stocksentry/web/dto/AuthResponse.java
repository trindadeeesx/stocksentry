package com.trindadeeesx.stocksentry.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
	private String token;
	private String email;
	private String role;
}
