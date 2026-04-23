package com.trindadeeesx.stocksentry.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
	@Email
	@NotBlank
	@Size(max = 150)
	private String email;

	@NotBlank
	@Size(max = 128)
	private String password;
}


