package com.trindadeeesx.stocksentry.infraestructure.security;

import com.trindadeeesx.stocksentry.domain.user.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {
    public User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public UUID getCurrentTenantId() {
        return getCurrentUser().getTenant().getId();
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
