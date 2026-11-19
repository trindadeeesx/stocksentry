package com.trindadeeesx.stocksentry.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String tenantName;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
    private String tenantSlug;

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;
}
