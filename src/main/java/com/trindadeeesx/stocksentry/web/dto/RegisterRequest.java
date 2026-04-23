package com.trindadeeesx.stocksentry.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
	@NotBlank
	@Size(max = 100)
	private String name;

	@Email
	@NotBlank
	@Size(max = 150)
	private String email;

	@NotBlank
	@Size(min = 8, max = 128)
	private String password;
}
