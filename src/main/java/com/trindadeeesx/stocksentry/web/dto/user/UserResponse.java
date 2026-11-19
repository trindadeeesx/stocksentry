package com.trindadeeesx.stocksentry.web.dto.user;

import com.trindadeeesx.stocksentry.domain.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private UserRole role;
    private boolean active;
    private LocalDateTime createdAt;
}
