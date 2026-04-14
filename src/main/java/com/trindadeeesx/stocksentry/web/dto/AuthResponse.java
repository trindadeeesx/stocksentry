package com.trindadeeesx.stocksentry.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private UUID tenantId;
}
